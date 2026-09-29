package cn.orangenode.forge.m4;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.RestTestClient;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.m3.M3FixtureService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * 真实 MinIO 的 S3 兼容验收。
 *
 * <p>整类以环境变量 {@code FORGE_S3_TEST_ENDPOINT} 为开关：该变量存在时必须真实通过，
 * 不存在时整类跳过（{@code @EnabledIfEnvironmentVariable}），因为本类需要可写的对象存储。
 * 跳过时不需要任何中间件，默认构建不会因此失败。</p>
 *
 * <p>连接参数由 {@code FORGE_S3_TEST_ENDPOINT}、{@code FORGE_S3_TEST_BUCKET}、
 * {@code FORGE_S3_TEST_ACCESS_KEY}、{@code FORGE_S3_TEST_SECRET_KEY} 提供；
 * 方案按 path-style 创建，与 MinIO 的寻址方式一致。验收顺序是连接检测、上传、下载内容一致、
 * 按文件 ID 删除，最后用 SDK 直接确认对象已不存在。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m4_minio;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.file.encryption-key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
})
@EnabledIfEnvironmentVariable(named = "FORGE_S3_TEST_ENDPOINT", matches = ".+")
class MinioS3IntegrationTest {

    /**
     * 未提供区域时使用的协议默认区域，MinIO 会忽略该值。
     */
    private static final String DEFAULT_REGION = "us-east-1";

    /**
     * 本地存储根目录，由 JUnit 在临时目录下创建并自动清理。
     */
    @TempDir
    static Path storageRoot;

    /**
     * 内存版 Redis 替身使用的字符串模板。
     */
    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 随机分配的测试端口。
     */
    @LocalServerPort
    private int port;

    /**
     * 建表与对象键查询使用的 JDBC 模板。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试数据夹具。
     */
    @Autowired
    private M3FixtureService fixture;

    /**
     * 认证与权限配置，提供超级管理员角色代码。
     */
    @Autowired
    private ForgeSecurityProperties securityProperties;

    /**
     * 按端口构建的测试客户端。
     */
    private RestTestClient client;

    /**
     * 内存版 Redis 替身。
     */
    private RedisTestDouble redisDouble;

    /**
     * 测试管理员的访问令牌。
     */
    private String token;

    /**
     * 把本地存储根目录与临时目录指向 JUnit 提供的临时目录。
     *
     * @param registry 动态属性注册表
     */
    @DynamicPropertySource
    static void registerFileDirectories(DynamicPropertyRegistry registry) {
        registry.add("forge.file.local-root", () -> storageRoot.toString());
        registry.add("forge.file.temp-dir", () -> storageRoot.resolve("tmp").toString());
    }

    /**
     * 每个用例前重建表、清空 Redis 替身，并以超级管理员身份登录。
     */
    @BeforeEach
    void prepareData() {
        client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        RuntimeSchema.recreateSystemAndAuditTables(jdbcTemplate);
        RuntimeSchema.recreateFileTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();
        M4TestSupport.seedPermissions(fixture, M4TestSupport.filePermissionCodes());
        M4TestSupport.createSuperAdmin(fixture, securityProperties.getSuperRoleCode());
        token = M4TestSupport.requireAccessToken(
                M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME, M4TestSupport.ADMIN_PASSWORD));
    }

    /**
     * 验证真实 MinIO 上的连接检测、上传、下载、删除与对象已不存在的完整链路。
     */
    @Test
    @DisplayName("真实 MinIO 上传下载删除全链路通过")
    void shouldRoundTripFileOnRealMinio() {
        String endpoint = requireEnvironment("FORGE_S3_TEST_ENDPOINT");
        String bucket = requireEnvironment("FORGE_S3_TEST_BUCKET");
        String accessKey = requireEnvironment("FORGE_S3_TEST_ACCESS_KEY");
        String secretKey = requireEnvironment("FORGE_S3_TEST_SECRET_KEY");
        String region = environmentOrDefault("FORGE_S3_TEST_REGION", DEFAULT_REGION);

        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH, M4TestSupport.s3ConfigBody("minio-live", "MinIO 验收方案", endpoint,
                        region, bucket, accessKey, secretKey, 1048576L),
                token);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        assertThat(created.body()).doesNotContain(accessKey).doesNotContain(secretKey);
        String configId = M4TestSupport.firstIdOf(created.body());

        M4TestSupport.ResponseSnapshot switched = M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/default", null, token);
        assertThat(M4TestSupport.codeOf(switched.body())).isZero();

        M4TestSupport.ResponseSnapshot tested = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/test", null, token);
        assertThat(tested.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(tested.body())).isZero();
        assertThat(tested.body()).as("真实 MinIO 必须可连接：%s", tested.body()).contains("\"available\":true");

        byte[] content = "真实 MinIO 验收内容-forge-m4".getBytes(StandardCharsets.UTF_8);
        M4TestSupport.ResponseSnapshot uploaded = M4TestSupport.upload(port, token, "MinIO验收.txt", "text/plain",
                content);
        assertThat(M4TestSupport.codeOf(uploaded.body())).isZero();
        String fileId = M4TestSupport.firstIdOf(uploaded.body());

        M4TestSupport.ResponseSnapshot downloaded = M4TestSupport.get(client,
                M4TestSupport.FILES_PATH + "/" + fileId + "/download", token);
        assertThat(downloaded.status()).isEqualTo(HttpStatus.OK);
        assertThat(downloaded.binary()).isEqualTo(content);

        String objectKey = jdbcTemplate.queryForObject("select object_key from file_record where id = ?",
                String.class, Long.valueOf(fileId));
        assertThat(objectKey).isNotBlank();

        M4TestSupport.ResponseSnapshot deleted = M4TestSupport.exchange(client, HttpMethod.DELETE,
                M4TestSupport.FILES_PATH + "/" + fileId, null, token);
        assertThat(M4TestSupport.codeOf(deleted.body())).isZero();

        M4TestSupport.ResponseSnapshot afterDelete = M4TestSupport.get(client,
                M4TestSupport.FILES_PATH + "/" + fileId + "/download", token);
        assertThat(M4TestSupport.codeOf(afterDelete.body())).isEqualTo(404);

        try (S3Client s3Client = buildClient(endpoint, region, accessKey, secretKey)) {
            Throwable missingObject = catchThrowable(() -> s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build()));
            assertThat(missingObject).as("删除后对象必须不存在").isInstanceOf(S3Exception.class);
            assertThat(((S3Exception) missingObject).statusCode()).isEqualTo(404);
        }
    }

    /**
     * 读取必需的环境变量，缺失时让用例直接失败。
     *
     * @param name 环境变量名
     * @return 环境变量值
     */
    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        assertThat(value).as("必须提供环境变量 %s", name).isNotBlank();
        return value;
    }

    /**
     * 读取可选的环境变量，缺失时返回默认值。
     *
     * @param name         环境变量名
     * @param defaultValue 默认值
     * @return 环境变量值或默认值
     */
    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    /**
     * 构造与生产适配同口径的同步对象存储客户端，用于直接确认对象状态。
     *
     * @param endpoint  访问地址
     * @param region    区域
     * @param accessKey 访问凭据
     * @param secretKey 访问密钥
     * @return 已装配的客户端，由调用方负责关闭
     */
    private static S3Client buildClient(String endpoint, String region, String accessKey, String secretKey) {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .endpointOverride(URI.create(endpoint))
                .build();
    }
}

package cn.orangenode.forge.m4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import cn.orangenode.forge.framework.crypto.CredentialCipher;
import cn.orangenode.forge.m3.M3FixtureService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

/**
 * 对象存储方案与凭据加密的端到端验证。
 *
 * <p>覆盖 S3 方案创建后的凭据落库形态：数据库中保存的是 AES-GCM 密文而不是明文，
 * 使用项目自身的 {@link CredentialCipher} 可以还原为原值，而接口响应既不回显明文也不回显密钥字段。
 * 另外验证不可达的访问地址在连接检测中返回可读结论，而不是抛出内部异常或回显地址。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m4_storage;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.file.encryption-key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY="
})
class FileStorageConfigIntegrationTest {

    /**
     * 本地存储根目录，由 JUnit 在临时目录下创建并自动清理。
     */
    @TempDir
    static Path storageRoot;

    /**
     * 测试使用的访问凭据明文，不是任何真实环境的密钥。
     */
    private static final String ACCESS_KEY = "m4-test-access-key";

    /**
     * 测试使用的访问密钥明文，不是任何真实环境的密钥。
     */
    private static final String SECRET_KEY = "m4-test-secret-key";

    /**
     * 不可达的对象存储访问地址，端口 1 上不会有服务监听。
     */
    private static final String UNREACHABLE_ENDPOINT = "http://127.0.0.1:1";

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
     * 建表与凭据落库断言使用的 JDBC 模板。
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
     * 项目自身的凭据加解密端口，用于验证密文可还原。
     */
    @Autowired
    private CredentialCipher credentialCipher;

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
     * 验证 S3 凭据以密文落库、可解密还原，且创建与列表响应都不回显明文。
     */
    @Test
    @DisplayName("S3 凭据加密落库且响应不回显明文")
    void shouldEncryptS3CredentialsAndHideThemFromResponse() {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH, M4TestSupport.s3ConfigBody("minio-plan", "对象存储方案",
                        UNREACHABLE_ENDPOINT, "us-east-1", "forge-m4-bucket", ACCESS_KEY, SECRET_KEY, 1048576L),
                token);

        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        assertThat(created.body()).contains("\"credentialConfigured\":true");
        assertThat(created.body()).doesNotContain(ACCESS_KEY).doesNotContain(SECRET_KEY);
        assertThat(created.body()).doesNotContain("accessKey").doesNotContain("secretKey");
        String configId = M4TestSupport.firstIdOf(created.body());

        M4TestSupport.ResponseSnapshot listed = M4TestSupport.get(client, M4TestSupport.STORAGE_CONFIGS_PATH, token);
        assertThat(M4TestSupport.codeOf(listed.body())).isZero();
        assertThat(listed.body()).doesNotContain(ACCESS_KEY).doesNotContain(SECRET_KEY);

        String storedAccessKey = jdbcTemplate.queryForObject(
                "select access_key from file_storage_config where id = ?", String.class, Long.valueOf(configId));
        String storedSecretKey = jdbcTemplate.queryForObject(
                "select secret_key from file_storage_config where id = ?", String.class, Long.valueOf(configId));
        assertThat(storedAccessKey).isNotNull().isNotEqualTo(ACCESS_KEY).doesNotContain(ACCESS_KEY);
        assertThat(storedSecretKey).isNotNull().isNotEqualTo(SECRET_KEY).doesNotContain(SECRET_KEY);
        assertThat(credentialCipher.decrypt(storedAccessKey)).isEqualTo(ACCESS_KEY);
        assertThat(credentialCipher.decrypt(storedSecretKey)).isEqualTo(SECRET_KEY);
    }

    /**
     * 验证不可达的访问地址在连接检测中返回 HTTP 200、body.code=0 与 available=false，且不回显地址。
     */
    @Test
    @DisplayName("不可达的对象存储地址返回 available=false 且不回显地址")
    void shouldReportUnavailableForUnreachableS3Endpoint() {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH, M4TestSupport.s3ConfigBody("minio-dead", "不可达方案",
                        UNREACHABLE_ENDPOINT, "us-east-1", "forge-m4-bucket", ACCESS_KEY, SECRET_KEY, 1048576L),
                token);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        String configId = M4TestSupport.firstIdOf(created.body());

        M4TestSupport.ResponseSnapshot tested = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/test", null, token);

        assertThat(tested.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(tested.body())).isZero();
        assertThat(tested.body()).contains("\"available\":false");
        assertThat(tested.body()).contains("\"provider\":\"s3\"");
        assertThat(tested.body()).doesNotContain("127.0.0.1").doesNotContain(ACCESS_KEY).doesNotContain(SECRET_KEY);
    }
}

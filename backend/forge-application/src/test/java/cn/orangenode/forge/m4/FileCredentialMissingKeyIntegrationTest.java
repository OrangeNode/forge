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
import cn.orangenode.forge.m3.M3FixtureService;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

/**
 * 缺少凭据加密主密钥时的端到端验证。
 *
 * <p>主密钥缺失不是启动期错误：本地存储不依赖主密钥，因此只有保存对象存储凭据的用例失败。
 * 本类以空的主密钥启动，验证 S3 方案创建明确失败且不写入任何配置行，
 * 同时验证本地存储方案仍可正常创建。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m4_no_key;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.file.encryption-key="
})
class FileCredentialMissingKeyIntegrationTest {

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
     * 建表与落库数量断言使用的 JDBC 模板。
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
     * 验证缺少主密钥时创建 S3 方案失败，提示指向主密钥，且不写入任何配置行。
     */
    @Test
    @DisplayName("缺少主密钥时 S3 方案创建失败且不落库")
    void shouldFailS3ConfigCreationWithoutMasterKey() {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH, M4TestSupport.s3ConfigBody("minio-plan", "对象存储方案",
                        "http://127.0.0.1:1", "us-east-1", "forge-m4-bucket", "m4-access", "m4-secret", 1048576L),
                token);

        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(created.body())).isEqualTo(500);
        assertThat(created.body()).contains("主密钥");
        assertThat(created.body()).doesNotContain("m4-access").doesNotContain("m4-secret");

        Integer rows = jdbcTemplate.queryForObject("select count(*) from file_storage_config", Integer.class);
        assertThat(rows).as("失败的创建不应留下配置行").isZero();
    }

    /**
     * 验证缺少主密钥不影响本地存储方案的创建：本地存储不使用凭据加密。
     */
    @Test
    @DisplayName("缺少主密钥时本地存储方案仍可创建")
    void shouldStillCreateLocalConfigWithoutMasterKey() {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("local-plan", "本地方案", "files", 1048576L, "txt"), token);

        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        assertThat(created.body()).contains("\"provider\":\"local\"");
    }
}

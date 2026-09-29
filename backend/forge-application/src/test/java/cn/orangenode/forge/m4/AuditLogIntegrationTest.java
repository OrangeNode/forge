package cn.orangenode.forge.m4;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;

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
import cn.orangenode.forge.m3.M3TestSupport;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

/**
 * 登录日志与操作日志的端到端验证。
 *
 * <p>登录日志覆盖成功、凭据错误、账号停用与触发限流四条路径，并断言按用户名与结果筛选的条数；
 * 操作日志覆盖成功写与失败写：失败既包含业务冲突（存储方案被文件引用时删除返回 409），
 * 也包含唯一约束冲突，并验证失败不会被记为成功。</p>
 *
 * <p>另外验证时间范围筛选只接受带时区的 ISO 8601，以及缺少 {@code audit:login:view} 权限时的拒绝。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m4_audit;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.file.encryption-key=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY=",
    "forge.security.login-max-attempts=3"
})
class AuditLogIntegrationTest {

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
     * 建表使用的 JDBC 模板。
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
     * 每个用例前重建表、清空 Redis 替身并写入权限种子与账号。
     *
     * <p>不在此处登录：登录本身会写登录日志，会让登录日志条数的断言失去确定性。</p>
     */
    @BeforeEach
    void prepareData() {
        client = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        RuntimeSchema.recreateSystemAndAuditTables(jdbcTemplate);
        RuntimeSchema.recreateFileTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();
        M4TestSupport.seedPermissions(fixture, M4TestSupport.filePermissionCodes());
        M4TestSupport.seedPermissions(fixture, M4TestSupport.auditPermissionCodes());
        M4TestSupport.createSuperAdmin(fixture, securityProperties.getSuperRoleCode());
        M4TestSupport.createDisabledAdmin(fixture);
    }

    /**
     * 验证登录成功、凭据错误、账号停用与触发限流都会落库，并可分别筛选。
     */
    @Test
    @DisplayName("登录成功与失败路径都会写入登录日志")
    void shouldRecordLoginLogsForAllLoginPaths() {
        String token = loginToken();

        assertThat(M4TestSupport.codeOf(M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME,
                "wrong-password").body())).isEqualTo(401);
        assertThat(M4TestSupport.codeOf(M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME,
                "wrong-password").body())).isEqualTo(401);
        assertThat(M4TestSupport.codeOf(M4TestSupport.login(client, M4TestSupport.BLOCKED_USERNAME,
                M4TestSupport.BLOCKED_PASSWORD).body())).isEqualTo(401);
        assertThat(M4TestSupport.codeOf(M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME,
                "wrong-password").body())).isEqualTo(401);
        assertThat(M4TestSupport.codeOf(M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME,
                M4TestSupport.ADMIN_PASSWORD).body())).isEqualTo(429);

        M4TestSupport.ResponseSnapshot all = M4TestSupport.get(client, M4TestSupport.LOGIN_LOGS_PATH, token);
        assertThat(M4TestSupport.codeOf(all.body())).isZero();
        assertThat(M4TestSupport.totalOf(all.body())).isEqualTo(6L);
        assertThat(all.body()).contains("\"username\":\"" + M4TestSupport.ADMIN_USERNAME + "\"");
        assertThat(all.body()).contains("\"result\":\"success\"").contains("\"result\":\"failure\"");
        assertThat(all.body()).contains("\"reason\":\"credentials\"");
        assertThat(all.body()).contains("\"reason\":\"disabled\"");
        assertThat(all.body()).contains("\"reason\":\"rate_limited\"");
        assertThat(all.body()).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");
        assertThat(all.body()).doesNotContain(M4TestSupport.ADMIN_PASSWORD);

        assertThat(M4TestSupport.totalOf(M4TestSupport.get(client,
                M4TestSupport.LOGIN_LOGS_PATH + "?result=success", token).body())).isEqualTo(1L);
        assertThat(M4TestSupport.totalOf(M4TestSupport.get(client,
                M4TestSupport.LOGIN_LOGS_PATH + "?result=failure", token).body())).isEqualTo(5L);
        assertThat(M4TestSupport.totalOf(M4TestSupport.get(client,
                M4TestSupport.LOGIN_LOGS_PATH + "?username=" + M4TestSupport.ADMIN_USERNAME, token).body()))
                        .isEqualTo(5L);
        assertThat(M4TestSupport.totalOf(M4TestSupport.get(client,
                M4TestSupport.LOGIN_LOGS_PATH + "?username=" + M4TestSupport.BLOCKED_USERNAME, token).body()))
                        .isEqualTo(1L);
    }

    /**
     * 验证成功写与失败写都会产生操作日志，身份、动作、对象、结果码与追踪编号齐全。
     */
    @Test
    @DisplayName("写接口成功与失败都写入操作日志")
    void shouldRecordOperationLogsForSuccessfulAndFailedWrites() {
        String token = loginToken();

        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.PERMISSIONS_PATH,
                M4TestSupport.permissionBody("m4:probe:create", "M4 探针权限"), token);
        assertThat(created.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();

        M4TestSupport.ResponseSnapshot duplicate = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.PERMISSIONS_PATH,
                M4TestSupport.permissionBody("m4:probe:create", "M4 探针权限"), token);
        assertThat(duplicate.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(duplicate.body())).isEqualTo(409);

        M4TestSupport.ResponseSnapshot permissionLogs = M4TestSupport.get(client,
                M4TestSupport.OPERATION_LOGS_PATH + "?action=system:permission:create", token);
        assertThat(M4TestSupport.codeOf(permissionLogs.body())).isZero();
        assertThat(M4TestSupport.totalOf(permissionLogs.body())).isEqualTo(2L);
        assertThat(permissionLogs.body()).contains("\"operatorType\":\"ADMIN\"");
        assertThat(permissionLogs.body()).contains("\"operatorName\":\"" + M3TestSupport.ADMIN_DISPLAY_NAME + "\"");
        assertThat(permissionLogs.body()).contains("\"action\":\"system:permission:create\"");
        assertThat(permissionLogs.body()).contains("\"resourceType\":\"permission\"");
        assertThat(permissionLogs.body()).contains("\"resultCode\":0");
        assertThat(permissionLogs.body()).containsPattern("\"traceId\":\"[0-9a-f]{32}\"");

        assertThat(M4TestSupport.totalOf(M4TestSupport.get(client, M4TestSupport.OPERATION_LOGS_PATH
                + "?action=system:permission:create&resultCode=0", token).body()))
                        .as("失败的操作不得记为成功").isEqualTo(1L);

        String configId = createLocalConfigWithUpload(token);
        M4TestSupport.ResponseSnapshot conflict = M4TestSupport.exchange(client, HttpMethod.DELETE,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId, null, token);
        assertThat(conflict.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(conflict.body())).isEqualTo(409);

        M4TestSupport.ResponseSnapshot deleteLogs = M4TestSupport.get(client,
                M4TestSupport.OPERATION_LOGS_PATH + "?action=file:storage:delete", token);
        assertThat(M4TestSupport.totalOf(deleteLogs.body())).isEqualTo(1L);
        assertThat(deleteLogs.body()).contains("\"resultCode\":409");
        assertThat(deleteLogs.body()).contains("\"resourceType\":\"storage-config\"");
        assertThat(deleteLogs.body()).contains("\"resourceId\":\"" + configId + "\"");
    }

    /**
     * 验证操作日志按带时区的 ISO 8601 时间范围筛选，缺少时区的时间返回 400。
     */
    @Test
    @DisplayName("操作日志按带时区的时间范围筛选且拒绝无时区时间")
    void shouldFilterOperationLogsByIsoTimeRange() {
        String token = loginToken();
        assertThat(M4TestSupport.codeOf(M4TestSupport.exchange(client, HttpMethod.POST, M4TestSupport.PERMISSIONS_PATH,
                M4TestSupport.permissionBody("m4:range:create", "M4 范围权限"), token).body())).isZero();

        String start = Instant.now().minusSeconds(3600).toString();
        String end = Instant.now().plusSeconds(3600).toString();
        M4TestSupport.ResponseSnapshot inRange = M4TestSupport.get(client,
                M4TestSupport.OPERATION_LOGS_PATH + "?startTime=" + start + "&endTime=" + end, token);
        assertThat(M4TestSupport.codeOf(inRange.body())).isZero();
        assertThat(M4TestSupport.totalOf(inRange.body())).isEqualTo(1L);
        assertThat(inRange.body()).containsPattern("\"createdAt\":\"\\d{4}-\\d{2}-\\d{2}T[^\"]+Z\"");

        M4TestSupport.ResponseSnapshot outOfRange = M4TestSupport.get(client, M4TestSupport.OPERATION_LOGS_PATH
                + "?startTime=2000-01-01T00:00:00Z&endTime=2000-01-02T00:00:00Z", token);
        assertThat(M4TestSupport.codeOf(outOfRange.body())).isZero();
        assertThat(M4TestSupport.totalOf(outOfRange.body())).isZero();

        M4TestSupport.ResponseSnapshot missingZone = M4TestSupport.get(client,
                M4TestSupport.OPERATION_LOGS_PATH + "?startTime=2026-09-29T10:00:00", token);
        assertThat(missingZone.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(missingZone.body())).isEqualTo(400);
    }

    /**
     * 验证只有文件查询权限的管理员访问审计接口被拒绝为 403。
     */
    @Test
    @DisplayName("缺少审计查询权限返回 403")
    void shouldRejectAuditQueryWithoutAuditPermission() {
        M4TestSupport.createLimitedAdmin(fixture, jdbcTemplate, M4TestSupport.LIMITED_ROLE_CODE,
                M4TestSupport.LIMITED_USERNAME, M4TestSupport.PERM_RECORD_VIEW);
        String limitedToken = M4TestSupport.requireAccessToken(M4TestSupport.login(client,
                M4TestSupport.LIMITED_USERNAME, M4TestSupport.LIMITED_PASSWORD));

        M4TestSupport.ResponseSnapshot fileList = M4TestSupport.get(client, M4TestSupport.FILES_PATH, limitedToken);
        assertThat(M4TestSupport.codeOf(fileList.body())).as("该角色确实拥有文件查询权限").isZero();

        M4TestSupport.ResponseSnapshot loginLogs = M4TestSupport.get(client, M4TestSupport.LOGIN_LOGS_PATH,
                limitedToken);
        assertThat(loginLogs.status()).isEqualTo(HttpStatus.OK);
        assertThat(M4TestSupport.codeOf(loginLogs.body())).isEqualTo(403);

        M4TestSupport.ResponseSnapshot operationLogs = M4TestSupport.get(client, M4TestSupport.OPERATION_LOGS_PATH,
                limitedToken);
        assertThat(M4TestSupport.codeOf(operationLogs.body())).isEqualTo(403);
    }

    /**
     * 以测试管理员身份登录并返回访问令牌。
     *
     * @return 访问令牌
     */
    private String loginToken() {
        return M4TestSupport.requireAccessToken(
                M4TestSupport.login(client, M4TestSupport.ADMIN_USERNAME, M4TestSupport.ADMIN_PASSWORD));
    }

    /**
     * 创建一个被文件引用的本地存储方案，用于验证业务冲突的操作日志。
     *
     * @param token 访问令牌
     * @return 存储配置版本 ID
     */
    private String createLocalConfigWithUpload(String token) {
        M4TestSupport.ResponseSnapshot created = M4TestSupport.exchange(client, HttpMethod.POST,
                M4TestSupport.STORAGE_CONFIGS_PATH,
                M4TestSupport.localConfigBody("audited-plan", "审计方案", "audited", 1048576L, "txt"), token);
        assertThat(M4TestSupport.codeOf(created.body())).isZero();
        String configId = M4TestSupport.firstIdOf(created.body());

        M4TestSupport.ResponseSnapshot switched = M4TestSupport.exchange(client, HttpMethod.PUT,
                M4TestSupport.STORAGE_CONFIGS_PATH + "/" + configId + "/default", null, token);
        assertThat(M4TestSupport.codeOf(switched.body())).isZero();

        M4TestSupport.ResponseSnapshot uploaded = M4TestSupport.upload(port, token, "审计文件.txt", "text/plain",
                "audit-content".getBytes(StandardCharsets.UTF_8));
        assertThat(M4TestSupport.codeOf(uploaded.body())).isZero();
        return configId;
    }
}

package cn.orangenode.forge.m3;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * 方法级权限校验的端到端验证。
 *
 * <p>认证接口本身只要求认证，因此使用受 {@code @PreAuthorize} 保护的探针接口验证
 * “已认证但缺权限返回 403”“有权限返回 200”“权限变更后下一次请求立即生效”，
 * 并验证超级管理员角色不需要逐条授权。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m3_rbac;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false"
})
class RbacIntegrationTest {

    /**
     * 从登录响应中提取令牌的正则。
     */
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

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
     * 建表与关系表写入使用的 JDBC 模板。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 测试数据夹具。
     */
    @Autowired
    private M3FixtureService fixture;

    /**
     * 按端口构建的测试客户端。
     */
    private RestTestClient restTestClient;

    /**
     * 内存版 Redis 替身。
     */
    private M3RedisDouble redisDouble;

    /**
     * 普通权限管理员 ID。
     */
    private Long adminId;

    /**
     * 普通角色 ID。
     */
    private Long roleId;

    /**
     * 探针权限 ID。
     */
    private Long probePermissionId;

    /**
     * 另一个权限 ID，用于验证权限按代码匹配。
     */
    private Long otherPermissionId;

    /**
     * 每个用例前重建表结构并写入基础数据，默认不授予探针权限。
     */
    @BeforeEach
    void prepareData() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        redisDouble = new M3RedisDouble(stringRedisTemplate);
        redisDouble.clear();
        roleId = fixture.createRole(M3TestSupport.ROLE_CODE, "运维角色");
        probePermissionId = fixture.createPermission(M3TestSupport.PROBE_PERMISSION, "探针查看");
        otherPermissionId = fixture.createPermission(M3TestSupport.OTHER_PERMISSION, "探针修改");
        adminId = fixture.createAdmin(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD, "enabled");
        fixture.grantRole(adminId, roleId);
    }

    /**
     * 验证未认证访问受保护探针返回 401。
     */
    @Test
    @DisplayName("未认证访问受保护探针返回 401")
    void shouldRejectUnauthenticatedProbe() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证已认证但缺少权限返回 403，且不返回业务数据。
     */
    @Test
    @DisplayName("已认证但无权限返回 403")
    void shouldRejectAuthenticatedRequestWithoutPermission() {
        String token = requireAccessToken();

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":403");
        assertThat(snapshot.body()).contains("\"data\":null");
        assertThat(snapshot.body()).doesNotContain("probe-ok");
    }

    /**
     * 验证拥有权限代码时放行。
     */
    @Test
    @DisplayName("拥有权限代码时放行")
    void shouldAllowRequestWithPermission() {
        fixture.linkRolePermission(roleId, probePermissionId);
        String token = requireAccessToken();

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);

        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("probe-ok");
    }

    /**
     * 验证其他权限不能替代所需权限：权限按代码匹配，不按“有任意权限”放行。
     */
    @Test
    @DisplayName("其他权限不能替代所需权限")
    void shouldNotTreatOtherPermissionAsAccess() {
        fixture.linkRolePermission(roleId, otherPermissionId);
        String token = requireAccessToken();

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);

        assertThat(snapshot.body()).contains("\"code\":403");
    }

    /**
     * 验证权限变更在下一次请求立即生效，不需要等待令牌过期。
     */
    @Test
    @DisplayName("权限变更立即生效")
    void shouldApplyPermissionChangeImmediately() {
        String token = requireAccessToken();
        assertThat(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token).body())
                .contains("\"code\":403");

        fixture.linkRolePermission(roleId, probePermissionId);
        assertThat(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token).body())
                .contains("\"code\":0");

        fixture.unlinkRolePermission(roleId, probePermissionId);
        assertThat(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token).body())
                .contains("\"code\":403");
    }

    /**
     * 验证超级管理员角色无需逐条授权即拥有全部有效权限。
     */
    @Test
    @DisplayName("超级管理员角色无需逐条授权")
    void shouldGrantSuperRoleAllPermissions() {
        Long superRoleId = fixture.createRole("super_admin", "超级管理员");
        Long superAdminId = fixture.createAdmin("super-admin-user", M3TestSupport.ADMIN_PASSWORD, "enabled");
        fixture.grantRole(superAdminId, superRoleId);
        String token = requireAccessToken("super-admin-user");

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);

        assertThat(snapshot.body()).contains("\"code\":0");
    }

    /**
     * 验证账号停用后其令牌无法访问受权限保护的接口。
     */
    @Test
    @DisplayName("账号停用后无法访问受保护接口")
    void shouldRejectProbeAfterAccountDisabled() {
        fixture.linkRolePermission(roleId, probePermissionId);
        String token = requireAccessToken();

        fixture.disableAdmin(adminId);

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);
        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 使用测试管理员登录并取出令牌。
     *
     * @return 访问令牌
     */
    private String requireAccessToken() {
        return requireAccessToken(M3TestSupport.ADMIN_USERNAME);
    }

    /**
     * 使用指定用户名登录并取出令牌。
     *
     * @param username 用户名
     * @return 访问令牌
     */
    private String requireAccessToken(String username) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + M3TestSupport.ADMIN_PASSWORD + "\"}";
        ResponseSnapshot snapshot = exchange(HttpMethod.POST, "/api/admin/v1/auth/login", body, null);
        Matcher matcher = ACCESS_TOKEN.matcher(snapshot.body());
        assertThat(matcher.find()).as("登录响应应包含 accessToken，实际响应为 %s", snapshot.body()).isTrue();
        return matcher.group(1);
    }

    /**
     * 发送请求并返回 UTF-8 解码后的响应。
     *
     * @param method 请求方法
     * @param uri    请求路径
     * @param body   请求体，允许为 {@code null}
     * @param token  Bearer 令牌，允许为 {@code null}
     * @return 响应快照
     */
    private ResponseSnapshot exchange(HttpMethod method, String uri, String body, String token) {
        RestTestClient.RequestBodySpec request = restTestClient.method(method).uri(uri);
        if (token != null) {
            request = request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RestTestClient.RequestHeadersSpec<?> spec = body == null
                ? request
                : request.contentType(MediaType.APPLICATION_JSON).body(body);
        EntityExchangeResult<byte[]> result = spec.exchange().returnResult(byte[].class);
        byte[] payload = result.getResponseBody();
        return new ResponseSnapshot(result.getStatus(),
                payload == null ? "" : new String(payload, StandardCharsets.UTF_8));
    }

    /**
     * 一次响应的关键信息。
     *
     * @param status HTTP 状态
     * @param body   UTF-8 解码后的响应体
     */
    private record ResponseSnapshot(HttpStatusCode status, String body) {
    }
}

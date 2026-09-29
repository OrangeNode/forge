package cn.orangenode.forge.m3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.framework.security.AdminTokenService;
import cn.orangenode.forge.system.config.ForgeSystemBootstrapProperties;
import cn.orangenode.forge.system.config.InitialAdminBootstrap;
import cn.orangenode.forge.system.mapper.SysAdminMapper;
import cn.orangenode.forge.system.mapper.SysAdminRoleMapper;
import cn.orangenode.forge.system.mapper.SysRoleMapper;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.system.service.AdminAuthorityService;

/**
 * 认证、令牌会话与登录限流的端到端验证。
 *
 * <p>在随机端口启动真实应用，使用内存数据库与内存版 Redis 替身，逐项断言 HTTP 状态与
 * {@code body.code}：登录成功、凭据错误、账号停用、令牌撤销、版本批量撤销、
 * 限流、依赖故障与未知路径不放行。</p>
 *
 * <p>令牌有效期与失败次数上限使用与配置文件不同的测试取值，用于证明这些行为来自配置。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m3_auth;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false",
    "forge.security.login-max-attempts=3",
    "forge.security.token-ttl-seconds=1800"
})
class AuthIntegrationTest {

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
     * 令牌会话组件，用于验证按账号批量撤销。
     */
    @Autowired
    private AdminTokenService tokenService;

    /**
     * 项目级 Redis 入口，用于读取键前缀做断言。
     */
    @Autowired
    private ForgeRedisTemplate forgeRedisTemplate;

    /**
     * 初始管理员引导，用于验证引导创建的账号可以真实登录。
     */
    @Autowired
    private InitialAdminBootstrap initialAdminBootstrap;

    /**
     * 认证与权限配置，构造新的引导实例时复用。
     */
    @Autowired
    private ForgeSecurityProperties securityProperties;

    /**
     * 管理员账号数据访问，构造新的引导实例时复用。
     */
    @Autowired
    private SysAdminMapper adminMapper;

    /**
     * 角色数据访问，构造新的引导实例时复用。
     */
    @Autowired
    private SysRoleMapper roleMapper;

    /**
     * 管理员与角色关系数据访问，构造新的引导实例时复用。
     */
    @Autowired
    private SysAdminRoleMapper adminRoleMapper;

    /**
     * 密码编码器，构造新的引导实例时复用。
     */
    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 权限与角色解析，用于在直接改库后显式失效权限缓存。
     */
    @Autowired
    private AdminAuthorityService authorityService;

    /**
     * 按端口构建的测试客户端。
     */
    private RestTestClient restTestClient;

    /**
     * 内存版 Redis 替身。
     */
    private RedisTestDouble redisDouble;

    /**
     * 测试管理员 ID。
     */
    private Long adminId;

    /**
     * 测试角色 ID。
     */
    private Long roleId;

    /**
     * 探针权限 ID。
     */
    private Long probePermissionId;

    /**
     * 每个用例前重建表结构、重置 Redis 替身并写入基础数据。
     */
    @BeforeEach
    void prepareData() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();
        roleId = fixture.createRole(M3TestSupport.ROLE_CODE, "运维角色");
        probePermissionId = fixture.createPermission(M3TestSupport.PROBE_PERMISSION, "探针查看");
        fixture.linkRolePermission(roleId, probePermissionId);
        adminId = fixture.createAdmin(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD, "enabled");
        fixture.grantRole(adminId, roleId);
    }

    /**
     * 验证登录成功返回令牌、Bearer 方案与配置的有效期，并只把摘要写入 Redis。
     */
    @Test
    @DisplayName("登录成功返回令牌与配置的有效期")
    void shouldLoginAndReturnConfiguredTokenTtl() {
        ResponseSnapshot snapshot = login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("\"tokenType\":\"Bearer\"");
        assertThat(snapshot.body()).contains("\"expiresIn\":1800");
        assertThat(snapshot.body()).contains("\"username\":\"" + M3TestSupport.ADMIN_USERNAME + "\"");
        assertThat(snapshot.body()).doesNotContain(M3TestSupport.ADMIN_PASSWORD);

        String token = requireAccessToken(snapshot);
        Set<String> keys = redisDouble.keys();
        assertThat(keys).hasSize(1);
        String sessionKey = keys.iterator().next();
        assertThat(sessionKey).matches(forgeRedisTemplate.getKeyPrefix() + ":auth:session:[0-9a-f]{64}");
        assertThat(sessionKey).doesNotContain(token);
        assertThat(redisDouble.ttl(sessionKey)).isEqualTo(Duration.ofSeconds(1800));
    }

    /**
     * 验证密码错误返回 401，且不返回业务数据。
     */
    @Test
    @DisplayName("密码错误返回 401")
    void shouldRejectWrongPassword() {
        ResponseSnapshot snapshot = login(M3TestSupport.ADMIN_USERNAME, "wrong-password");

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":401");
        assertThat(snapshot.body()).contains("\"data\":null");
    }

    /**
     * 验证账号不存在与密码错误返回相同的提示，不暴露账号是否存在。
     */
    @Test
    @DisplayName("账号不存在与密码错误返回同一提示")
    void shouldNotRevealWhetherAccountExists() {
        ResponseSnapshot unknownAccount = login("not-exists-admin", M3TestSupport.ADMIN_PASSWORD);
        ResponseSnapshot wrongPassword = login(M3TestSupport.ADMIN_USERNAME, "wrong-password");

        assertThat(unknownAccount.body()).contains("\"code\":401");
        assertThat(messageOf(unknownAccount)).isEqualTo(messageOf(wrongPassword));
    }

    /**
     * 验证停用账号无法登录。
     */
    @Test
    @DisplayName("停用账号无法登录")
    void shouldRejectDisabledAccountLogin() {
        fixture.disableAdmin(adminId);

        ResponseSnapshot snapshot = login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD);

        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证用户名不区分大小写：保存与查询都使用规范化后的用户名。
     */
    @Test
    @DisplayName("用户名不区分大小写")
    void shouldLoginWithDifferentUsernameCase() {
        ResponseSnapshot snapshot = login(M3TestSupport.ADMIN_USERNAME.toUpperCase(Locale.ROOT),
                M3TestSupport.ADMIN_PASSWORD);

        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("\"username\":\"" + M3TestSupport.ADMIN_USERNAME + "\"");
    }

    /**
     * 验证未携带令牌访问受保护接口返回 401。
     */
    @Test
    @DisplayName("未携带令牌访问受保护接口返回 401")
    void shouldRejectProtectedRequestWithoutToken() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证未知路径在未认证时同样不放行，返回 401 而不是 404。
     */
    @Test
    @DisplayName("未认证访问未知路径返回 401")
    void shouldRejectUnknownPathWithoutToken() {
        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/m3/not-exists", null, null);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证已认证访问未知路径仍走框架默认错误出口，返回 404。
     */
    @Test
    @DisplayName("已认证访问未知路径返回 404")
    void shouldReturnNotFoundForUnknownPathWithToken() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/m3/not-exists", null, token);

        assertThat(snapshot.body()).contains("\"code\":404");
    }

    /**
     * 验证当前身份返回管理员信息与实时权限，且认证主体来自安全上下文。
     */
    @Test
    @DisplayName("当前身份返回实时权限并可由探针读取主体")
    void shouldReturnProfileWithRealtimePermissions() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot profile = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);
        assertThat(profile.body()).contains("\"code\":0");
        assertThat(profile.body()).contains("\"displayName\":\"" + M3TestSupport.ADMIN_DISPLAY_NAME + "\"");
        assertThat(profile.body()).contains(M3TestSupport.PROBE_PERMISSION);

        // 直接改库不会经过服务层的缓存失效，因此这里显式递增权限版本；
        // 走管理接口修改授权的自动失效由 M5 的系统管理用例验证。
        Long newPermissionId = fixture.createPermission(M3TestSupport.OTHER_PERMISSION, "探针修改");
        fixture.linkRolePermission(roleId, newPermissionId);
        authorityService.evictAllPermissions();
        ResponseSnapshot afterChange = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);
        assertThat(afterChange.body()).contains(M3TestSupport.OTHER_PERMISSION);

        ResponseSnapshot principal = exchange(HttpMethod.GET, M3TestSupport.PRINCIPAL_PROBE, null, token);
        assertThat(principal.body()).contains("\"username\":\"" + M3TestSupport.ADMIN_USERNAME + "\"");
        assertThat(principal.body()).doesNotContain("passwordHash");
    }

    /**
     * 验证菜单只返回角色已授予的节点。
     */
    @Test
    @DisplayName("菜单只返回角色已授予的节点")
    void shouldReturnOnlyGrantedMenus() {
        Long grantedMenuId = fixture.createMenu(0L, "概览", "home", 0);
        fixture.createMenu(0L, "未授权页面", "not-granted", 1);
        fixture.linkRoleMenu(roleId, grantedMenuId);
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/menus", null, token);

        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("\"routeKey\":\"home\"");
        assertThat(snapshot.body()).doesNotContain("not-granted");
    }

    /**
     * 验证超级管理员角色可见全部菜单：菜单可见性与权限使用同一条超级管理员语义，
     * 否则空库安装后初始管理员会因为没有任何角色菜单关系而看不到菜单。
     */
    @Test
    @DisplayName("超级管理员可见全部菜单")
    void shouldReturnAllMenusForSuperAdmin() {
        Long superRoleId = fixture.createRole(securityProperties.getSuperRoleCode(), "超级管理员");
        fixture.createMenu(0L, "概览", "home", 0);
        fixture.createMenu(0L, "未授权页面", "not-granted", 1);
        Long superAdminId = fixture.createAdmin("super-admin-user", M3TestSupport.ADMIN_PASSWORD, "enabled");
        fixture.grantRole(superAdminId, superRoleId);
        String token = requireAccessToken(login("super-admin-user", M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/menus", null, token);

        assertThat(snapshot.body()).contains("\"code\":0");
        assertThat(snapshot.body()).contains("\"routeKey\":\"home\"");
        assertThat(snapshot.body()).contains("\"routeKey\":\"not-granted\"");
    }

    /**
     * 验证退出登录后原令牌立即失效。
     */
    @Test
    @DisplayName("退出登录后原令牌立即失效")
    void shouldInvalidateTokenAfterLogout() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot logout = exchange(HttpMethod.POST, "/api/admin/v1/auth/logout", null, token);
        assertThat(logout.body()).contains("\"code\":0");

        ResponseSnapshot afterLogout = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);
        assertThat(afterLogout.body()).contains("\"code\":401");
        assertThat(redisDouble.keys()).noneMatch(key -> key.contains(":auth:session:"));
    }

    /**
     * 验证账号停用后既有令牌在下一次请求即失效。
     */
    @Test
    @DisplayName("账号停用后既有令牌立即失效")
    void shouldInvalidateTokenWhenAccountDisabled() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        fixture.disableAdmin(adminId);

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);
        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证按账号批量撤销后旧令牌全部失效，重新登录签发的新令牌可用。
     */
    @Test
    @DisplayName("批量撤销后旧令牌失效且新令牌可用")
    void shouldInvalidateAllTokensAfterVersionRevoked() {
        String first = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));
        String second = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        tokenService.revokeAllForAdmin(adminId);

        assertThat(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, first).body()).contains("\"code\":401");
        assertThat(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, second).body()).contains("\"code\":401");

        String renewed = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));
        assertThat(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, renewed).body()).contains("\"code\":0");
    }

    /**
     * 验证篡改后的令牌被拒绝。
     */
    @Test
    @DisplayName("篡改令牌被拒绝")
    void shouldRejectTamperedToken() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token + "tampered");

        assertThat(snapshot.body()).contains("\"code\":401");
    }

    /**
     * 验证同一用户名连续失败达到上限后返回 429，包括使用正确密码的再次尝试。
     */
    @Test
    @DisplayName("登录失败达到上限后返回 429")
    void shouldRateLimitRepeatedLoginFailures() {
        for (int attempt = 0; attempt < 3; attempt++) {
            assertThat(login(M3TestSupport.ADMIN_USERNAME, "wrong-password").body()).contains("\"code\":401");
        }

        ResponseSnapshot blocked = login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD);

        assertThat(blocked.status()).isEqualTo(HttpStatus.OK);
        assertThat(blocked.body()).contains("\"code\":429");
    }

    /**
     * 验证登录成功会清除失败计数，不会因为历史失败次数被继续限制。
     */
    @Test
    @DisplayName("登录成功清除失败计数")
    void shouldResetFailureCountAfterSuccessfulLogin() {
        assertThat(login(M3TestSupport.ADMIN_USERNAME, "wrong-password").body()).contains("\"code\":401");
        assertThat(login(M3TestSupport.ADMIN_USERNAME, "wrong-password").body()).contains("\"code\":401");
        assertThat(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD).body()).contains("\"code\":0");

        assertThat(login(M3TestSupport.ADMIN_USERNAME, "wrong-password").body()).contains("\"code\":401");
        assertThat(login(M3TestSupport.ADMIN_USERNAME, "wrong-password").body()).contains("\"code\":401");
        assertThat(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD).body()).contains("\"code\":0");
    }

    /**
     * 验证缓存依赖不可用时受保护请求返回 503，且不返回业务数据。
     */
    @Test
    @DisplayName("Redis 不可用时受保护请求返回 503")
    void shouldReturnServiceUnavailableWhenRedisUnavailable() {
        String token = requireAccessToken(login(M3TestSupport.ADMIN_USERNAME, M3TestSupport.ADMIN_PASSWORD));
        redisDouble.failOnRead();

        ResponseSnapshot snapshot = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);

        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(snapshot.body()).contains("\"code\":503");
        assertThat(snapshot.body()).doesNotContain(M3TestSupport.ADMIN_USERNAME);
    }

    /**
     * 验证初始管理员引导创建的账号可以真实登录并拥有超级管理员权限。
     */
    @Test
    @DisplayName("引导创建的初始管理员可以登录")
    void shouldLoginWithBootstrappedAdmin() {
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        redisDouble.clear();
        fixture.createRole(securityProperties.getSuperRoleCode(), "超级管理员");
        ForgeSystemBootstrapProperties bootstrapProperties = new ForgeSystemBootstrapProperties();
        bootstrapProperties.setUsername("bootstrap-admin");
        bootstrapProperties.setPassword("bootstrap-pass");
        new InitialAdminBootstrap(bootstrapProperties, securityProperties, adminMapper, roleMapper, adminRoleMapper,
                passwordEncoder).run(null);

        ResponseSnapshot snapshot = login("bootstrap-admin", "bootstrap-pass");

        assertThat(snapshot.body()).contains("\"code\":0");
        String token = requireAccessToken(snapshot);
        ResponseSnapshot profile = exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token);
        assertThat(profile.body()).contains("\"username\":\"bootstrap-admin\"");
    }

    /**
     * 验证引导在缺少凭据且没有任何账号时明确失败。
     */
    @Test
    @DisplayName("缺少凭据且没有账号时引导明确失败")
    void shouldFailBootstrapWithoutCredentials() {
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        ForgeSystemBootstrapProperties bootstrapProperties = new ForgeSystemBootstrapProperties();
        InitialAdminBootstrap bootstrap = new InitialAdminBootstrap(bootstrapProperties, securityProperties, adminMapper,
                roleMapper, adminRoleMapper, passwordEncoder);

        assertThatThrownBy(() -> bootstrap.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FORGE_INIT_ADMIN_USERNAME");
    }

    /**
     * 发送登录请求。
     *
     * @param username 用户名
     * @param password 密码
     * @return 响应快照
     */
    private ResponseSnapshot login(String username, String password) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return exchange(HttpMethod.POST, "/api/admin/v1/auth/login", body, null);
    }

    /**
     * 从登录响应中取出令牌，取不到时让用例直接失败。
     *
     * @param snapshot 登录响应快照
     * @return 访问令牌
     */
    private String requireAccessToken(ResponseSnapshot snapshot) {
        Matcher matcher = ACCESS_TOKEN.matcher(snapshot.body());
        assertThat(matcher.find()).as("登录响应应包含 accessToken，实际响应为 %s", snapshot.body()).isTrue();
        return matcher.group(1);
    }

    /**
     * 读取响应体中的 message 字段。
     *
     * @param snapshot 响应快照
     * @return message 内容，缺失时返回空字符串
     */
    private String messageOf(ResponseSnapshot snapshot) {
        Matcher matcher = Pattern.compile("\"message\":\"([^\"]*)\"").matcher(snapshot.body());
        return matcher.find() ? matcher.group(1) : "";
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
        return new ResponseSnapshot(result.getStatus(), payload == null ? "" : new String(payload, StandardCharsets.UTF_8));
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

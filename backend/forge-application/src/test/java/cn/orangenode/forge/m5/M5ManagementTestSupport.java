package cn.orangenode.forge.m5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
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

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.m3.M3FixtureService;
import cn.orangenode.forge.m3.M3TestSupport;
import cn.orangenode.forge.support.RedisTestDouble;
import cn.orangenode.forge.support.RuntimeSchema;

/**
 * M5 系统管理接口端到端验证的公共装配。
 *
 * <p>管理员、角色与菜单的管理接口都要求认证并逐项校验权限代码；权限标识由菜单节点声明，
 * 没有独立的权限管理接口。因此每个用例都先重建表结构、清空 Redis 替身，再准备一个拥有全部有效权限的
 * 超级管理员：它按 {@code forge.security.super-role-code} 识别，不需要逐条授予权限，
 * 用它取得的令牌调用管理接口。表结构与关系写入复用 M3 已交付的 {@link M3FixtureService}，
 * 不在 M5 重写一套夹具。</p>
 *
 * <p>本类不声明用例，只提供装配与 HTTP 断言辅助；子用例类继承同一套装配，
 * 每个用例执行前重建表结构，因此多个用例类可以共享一个内存库与上下文。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.dynamic.datasource.master.url=jdbc:h2:mem:forge_m5;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.dynamic.datasource.master.username=sa",
    "spring.datasource.dynamic.datasource.master.password=",
    "spring.datasource.dynamic.datasource.master.driverClassName=org.h2.Driver",
    "spring.flyway.enabled=false",
    "forge.mybatis-plus.db-type=H2",
    "forge.system.bootstrap.enabled=false"
})
abstract class M5ManagementTestSupport {

    /**
     * 管理员管理接口根路径。
     */
    protected static final String ADMIN_PATH = "/api/admin/v1/system/admins";

    /**
     * 角色管理接口根路径。
     */
    protected static final String ROLE_PATH = "/api/admin/v1/system/roles";

    /**
     * 菜单管理接口根路径。
     */
    protected static final String MENU_PATH = "/api/admin/v1/system/menus";

    /**
     * 管理员列表所需的查看权限，用于验证“已认证但缺权限”。
     */
    protected static final String ADMIN_VIEW_PERMISSION = "system:admin:view";

    /**
     * 超级管理员用户名。
     */
    protected static final String SUPER_ADMIN_USERNAME = "m5-super-admin";

    /**
     * 普通管理员用户名，用于验证权限缓存失效的真实效果。
     */
    protected static final String PLAIN_ADMIN_USERNAME = "m5-plain-admin";

    /**
     * 普通角色代码。
     */
    protected static final String PLAIN_ROLE_CODE = "m5_plain_role";

    /**
     * 所有接口共用的登录密码，满足创建密码的长度规则。
     */
    protected static final String PASSWORD = M3TestSupport.ADMIN_PASSWORD;

    /**
     * 从登录响应中提取令牌的正则。
     */
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"accessToken\":\"([^\"]+)\"");

    /**
     * 从响应体中提取 id 字段的正则。
     */
    private static final Pattern ID_FIELD = Pattern.compile("\"id\":\"(\\d+)\"");

    /**
     * 从响应体中提取数组字段的正则模板，字段名由调用方给出。
     */
    private static final String ARRAY_FIELD_TEMPLATE = "\"%s\":\\[([^\\]]*)\\]";

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
     * M3 已交付的数据夹具，M5 直接复用它的管理员、角色、菜单与关系写入。
     */
    @Autowired
    private M3FixtureService fixture;

    /**
     * 认证与权限配置，超级管理员角色代码取自配置而不是写死在用例里。
     */
    @Autowired
    private ForgeSecurityProperties securityProperties;

    /**
     * 按端口构建的测试客户端。
     */
    private RestTestClient restTestClient;

    /**
     * 内存版 Redis 替身。
     */
    private RedisTestDouble redisDouble;

    /**
     * 超级管理员 ID。
     */
    private Long superAdminId;

    /**
     * 承载探针权限的菜单节点 ID，供权限缓存用例复现“先 403 再 200”。
     *
     * <p>权限标识由菜单节点声明，因此授予探针权限就是授予 {@link RuntimeSchema#seedPermissions} 建立的
     * 集成测试权限节点。</p>
     */
    private Long probeMenuId;

    /**
     * 每个用例前重建表结构、清空 Redis 替身并准备超级管理员。
     *
     * <p>超级管理员在用例开始前已经存在于上下文缓存中，但重新建表与清空 Redis 都发生在
     * 它被解析之前，因此它看到的仍是本用例的表结构。</p>
     */
    @BeforeEach
    void prepareManagementData() {
        restTestClient = RestTestClient.bindToServer().baseUrl("http://127.0.0.1:" + port).build();
        M3TestSupport.recreateSystemTables(jdbcTemplate);
        redisDouble = new RedisTestDouble(stringRedisTemplate);
        redisDouble.clear();

        RuntimeSchema.seedPermissions(jdbcTemplate);
        probeMenuId = queryLong("select id from sys_menu where route_key = ?",
                RuntimeSchema.PERMISSION_MENU_ROUTE_KEY);
        Long superRoleId = fixture.createRole(securityProperties.getSuperRoleCode(), "超级管理员");
        superAdminId = fixture.createAdmin(SUPER_ADMIN_USERNAME, PASSWORD, "enabled");
        fixture.grantRole(superAdminId, superRoleId);
    }

    /**
     * 取得超级管理员登录响应，供需要同时读取令牌与当前管理员 ID 的用例使用。
     *
     * @return 登录响应快照
     */
    protected ResponseSnapshot superAdminLogin() {
        return loginExchange(SUPER_ADMIN_USERNAME, PASSWORD);
    }

    /**
     * 取得超级管理员令牌。
     *
     * @return 超级管理员访问令牌
     */
    protected String superToken() {
        return login(SUPER_ADMIN_USERNAME, PASSWORD);
    }

    /**
     * 读取内置超级管理员角色 ID，夹具构造的数据用主键定位以免受名称文案影响。
     *
     * @return 超级管理员角色 ID
     */
    protected Long builtInSuperRoleId() {
        return queryLong("select id from sys_role where code = ? and deleted = 0",
                securityProperties.getSuperRoleCode());
    }

    /**
     * 查询单个 Long 值，用于定位夹具写入的数据。
     *
     * @param sql    查询语句
     * @param params 语句参数
     * @return 查询结果，无结果时为 {@code null}
     */
    protected Long queryLong(String sql, Object... params) {
        List<Long> values = jdbcTemplate.queryForList(sql, Long.class, params);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 取得承载探针权限的菜单节点 ID。
     *
     * @return 集成测试权限节点 ID
     */
    protected Long probeMenuId() {
        return probeMenuId;
    }

    /**
     * 读取菜单节点当前声明的权限标识列内容。
     *
     * <p>用例需要在该节点原有声明的基础上增删某个代码时，先从数据库读出原文，
     * 避免在测试里再抄一份权限清单。</p>
     *
     * @param menuId 菜单 ID
     * @return 逗号分隔的权限标识列内容，节点未声明权限时返回 {@code null}
     */
    protected String menuPermissionCodeText(Long menuId) {
        List<String> values = jdbcTemplate.queryForList("select perm_codes from sys_menu where id = ?",
                String.class, menuId);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * 按菜单行的现有名称、父菜单、路由标识与顺序构造修改请求体，只替换声明的权限标识。
     *
     * <p>修改接口要求提交完整字段，用例只关心权限标识的变化，因此其余字段从库里读回，
     * 不在用例中硬编码夹具节点的名称与路由标识。</p>
     *
     * @param menuId    菜单 ID
     * @param permCodes 该节点最终声明的权限标识，允许为空表示不再声明权限
     * @return JSON 请求体
     */
    protected String menuUpdateBody(Long menuId, List<String> permCodes) {
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select parent_id, name, route_key, sort_no from sys_menu where id = ?", menuId);
        Object routeKey = row.get("route_key");
        String codes = String.join(",", permCodes.stream().map(code -> "\"" + code + "\"").toList());
        return "{\"parentId\":\"" + row.get("parent_id") + "\",\"name\":\"" + row.get("name")
                + "\",\"routeKey\":" + (routeKey == null ? "null" : "\"" + routeKey + "\"")
                + ",\"permCodes\":[" + codes + "],\"sortNo\":" + row.get("sort_no") + "}";
    }

    /**
     * 取得数据夹具。
     *
     * @return M3 数据夹具
     */
    protected M3FixtureService fixture() {
        return fixture;
    }

    /**
     * 取得内存版 Redis 替身，用于断言权限缓存键的真实读写。
     *
     * @return Redis 替身
     */
    protected RedisTestDouble redisDouble() {
        return redisDouble;
    }

    /**
     * 记录一条角色与菜单关系，直接改库用于构造初始授权组合。
     *
     * <p>角色授予菜单节点即同时获得该节点声明的权限标识，因此不再有独立的权限关系。</p>
     *
     * @param roleId 角色 ID
     * @param menuId 菜单 ID
     */
    protected void linkRoleMenu(Long roleId, Long menuId) {
        fixture.linkRoleMenu(roleId, menuId);
    }

    /**
     * 从登录响应快照中取出访问令牌。
     *
     * @param snapshot 登录响应快照
     * @return 访问令牌
     */
    protected String tokenOf(ResponseSnapshot snapshot) {
        Matcher matcher = ACCESS_TOKEN.matcher(snapshot.body());
        assertThat(matcher.find()).as("登录响应应包含 accessToken，实际响应为 %s", snapshot.body()).isTrue();
        return matcher.group(1);
    }

    /**
     * 登录并取出访问令牌，登录失败时让用例立即失败。
     *
     * @param username 用户名
     * @param password 密码
     * @return 访问令牌
     */
    protected String login(String username, String password) {
        return tokenOf(loginExchange(username, password));
    }

    /**
     * 执行登录并返回完整响应，供需要读取当前管理员 ID 的用例使用。
     *
     * @param username 用户名
     * @param password 密码
     * @return 登录响应快照
     */
    protected ResponseSnapshot loginExchange(String username, String password) {
        String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        return exchange(HttpMethod.POST, "/api/admin/v1/auth/login", body, null);
    }

    /**
     * 发送请求并返回 UTF-8 解码后的响应。
     *
     * @param method 请求方法
     * @param uri    请求路径，可自带查询字符串
     * @param body   请求体，允许为 {@code null}
     * @param token  Bearer 令牌，允许为 {@code null}
     * @return 响应快照
     */
    protected ResponseSnapshot exchange(HttpMethod method, String uri, String body, String token) {
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
     * 断言响应为 HTTP 200 且业务码为 0。
     *
     * @param snapshot 响应快照
     * @return 响应体，便于继续断言业务字段
     */
    protected String assertOk(ResponseSnapshot snapshot) {
        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(codeOf(snapshot)).as("响应体=%s", snapshot.body()).isEqualTo(0);
        return snapshot.body();
    }

    /**
     * 断言响应为 HTTP 200 且业务码为期望的失败码。
     *
     * @param snapshot 响应快照
     * @param expected 期望的业务码
     * @return 响应体，便于继续断言提示或字段错误
     */
    protected String assertCode(ResponseSnapshot snapshot, int expected) {
        assertThat(snapshot.status()).isEqualTo(HttpStatus.OK);
        assertThat(codeOf(snapshot)).as("期望 %s，响应体=%s", expected, snapshot.body()).isEqualTo(expected);
        return snapshot.body();
    }

    /**
     * 读取响应体中的业务码。
     *
     * @param snapshot 响应快照
     * @return 业务码
     */
    protected int codeOf(ResponseSnapshot snapshot) {
        Matcher matcher = Pattern.compile("\"code\":(-?\\d+)").matcher(snapshot.body());
        assertThat(matcher.find()).as("响应体应包含 code 字段，实际响应为 %s", snapshot.body()).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    /**
     * 读取响应体中对象或顶层对象的 id 字段。
     *
     * @param body 响应体
     * @return 字符串 ID
     */
    protected String idOf(String body) {
        Matcher matcher = ID_FIELD.matcher(body);
        assertThat(matcher.find()).as("响应体应包含 id 字段，实际响应为 %s", body).isTrue();
        return matcher.group(1);
    }

    /**
     * 读取响应体中指定数组字段的全部字符串元素。
     *
     * @param body  响应体
     * @param field 数组字段名
     * @return 去掉引号后的元素列表
     */
    protected List<String> arrayOf(String body, String field) {
        Matcher matcher = Pattern.compile(ARRAY_FIELD_TEMPLATE.formatted(field)).matcher(body);
        assertThat(matcher.find()).as("响应体应包含数组字段 %s，实际响应为 %s", field, body).isTrue();
        String content = matcher.group(1).trim();
        if (content.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(content.split(","))
                .map(item -> item.trim().replace("\"", ""))
                .toList();
    }

    /**
     * 读取响应体中对象数组字段里每个元素的 ID。
     *
     * <p>对象数组不能按逗号切分元素，否则每个字段都会被当成一个元素；
     * 这里只提取数组内容中的 ID，用于断言角色等对象集合。</p>
     *
     * @param body  响应体
     * @param field 数组字段名
     * @return 元素 ID 列表
     */
    protected List<String> idsOf(String body, String field) {
        Matcher arrayMatcher = Pattern.compile(ARRAY_FIELD_TEMPLATE.formatted(field)).matcher(body);
        assertThat(arrayMatcher.find()).as("响应体应包含数组字段 %s，实际响应为 %s", field, body).isTrue();
        Matcher idMatcher = Pattern.compile("\"id\":\"(\\d+)\"").matcher(arrayMatcher.group(1));
        List<String> ids = new java.util.ArrayList<>();
        while (idMatcher.find()) {
            ids.add(idMatcher.group(1));
        }
        return ids;
    }

    /**
     * 统计表中满足条件的行数，用于验证全量替换没有产生重复关系。
     *
     * @param sql    统计语句
     * @param params 语句参数
     * @return 行数
     */
    protected int countOf(String sql, Object... params) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count == null ? 0 : count;
    }

    /**
     * 一次响应的关键信息。
     *
     * @param status HTTP 状态
     * @param body   UTF-8 解码后的响应体
     */
    protected record ResponseSnapshot(HttpStatusCode status, String body) {
    }
}

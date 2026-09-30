package cn.orangenode.forge.m5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * 管理员管理接口的端到端验证。
 *
 * <p>覆盖分页查询与筛选、详情、新增（用户名规范化、重复用户名、密码长度）、修改显示名称、
 * 启停（停用后既有令牌失效、不能停用自己）、重置密码（旧令牌失效、新密码可登录）与角色全量替换，
 * 并验证无 {@code system:admin:view} 权限返回 403、未带令牌返回 401。</p>
 *
 * <p>断言同时覆盖 HTTP 状态与 {@code body.code}；每次调用都使用管理接口，不绕过 Controller
 * 直接改库，从而验证接口、服务与统一错误出口的完整路径。</p>
 */
class AdminManagementIntegrationTest extends M5ManagementTestSupport {

    /**
     * 验证分页查询支持用户名关键字与状态筛选，并返回真实总数与分页字段。
     */
    @Test
    @DisplayName("分页查询支持用户名与状态筛选")
    void shouldPageAdminsWithUsernameAndStatusFilters() {
        fixture().createAdmin("m5-filter-alpha", PASSWORD, "enabled");
        fixture().createAdmin("m5-filter-beta", PASSWORD, "disabled");

        String all = assertOk(exchange(HttpMethod.GET, ADMIN_PATH + "?pageNum=1&pageSize=10", null, superToken()));

        assertThat(all).contains("\"total\":3");
        assertThat(all).contains("\"pageNum\":1");
        assertThat(all).contains("\"pageSize\":10");

        String byUsername = assertOk(exchange(HttpMethod.GET, ADMIN_PATH + "?username=ALPHA", null, superToken()));
        assertThat(byUsername).contains("m5-filter-alpha").doesNotContain("m5-filter-beta");

        String byStatus = assertOk(exchange(HttpMethod.GET, ADMIN_PATH + "?status=disabled", null, superToken()));
        assertThat(byStatus).contains("m5-filter-beta").doesNotContain("m5-filter-alpha");
    }

    /**
     * 验证新增管理员时用户名被规范化为小写，并且可以同时分配初始角色。
     */
    @Test
    @DisplayName("新增管理员规范化用户名并分配初始角色")
    void shouldCreateAdminWithNormalizedUsernameAndRoles() {
        Long roleId = fixture().createRole("m5_created_role", "新增角色");
        String body = "{\"username\":\"  M5-New-Admin  \",\"password\":\"" + PASSWORD
                + "\",\"displayName\":\"新建管理员\",\"roleIds\":[\"" + roleId + "\"]}";

        String created = assertOk(exchange(HttpMethod.POST, ADMIN_PATH, body, superToken()));

        assertThat(created).contains("\"username\":\"m5-new-admin\"");
        assertThat(created).contains("\"displayName\":\"新建管理员\"");
        assertThat(created).contains("\"status\":\"enabled\"");
        assertThat(created).doesNotContain(PASSWORD);
        assertThat(idsOf(created, "roles")).containsExactly(String.valueOf(roleId));
        assertThat(login("m5-new-admin", PASSWORD)).isNotBlank();
    }

    /**
     * 验证规范化后重名的用户名返回 409，而不是创建第二个账号。
     */
    @Test
    @DisplayName("重复用户名返回 409")
    void shouldRejectDuplicateUsername() {
        fixture().createAdmin("m5-dup-admin", PASSWORD, "enabled");
        String body = "{\"username\":\"M5-DUP-ADMIN\",\"password\":\"" + PASSWORD + "\",\"displayName\":\"重复账号\"}";

        String rejected = assertCode(exchange(HttpMethod.POST, ADMIN_PATH, body, superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_admin where username = ?", "m5-dup-admin")).isEqualTo(1);
    }

    /**
     * 验证密码长度不足返回 400，且携带字段错误明细而不回显密码原值。
     */
    @Test
    @DisplayName("密码过短返回 400")
    void shouldRejectShortPassword() {
        String body = "{\"username\":\"m5-short-pass\",\"password\":\"1234\",\"displayName\":\"密码过短\"}";

        String rejected = assertCode(exchange(HttpMethod.POST, ADMIN_PATH, body, superToken()), 400);

        assertThat(rejected).contains("\"fieldErrors\"");
        assertThat(rejected).contains("\"field\":\"password\"");
    }

    /**
     * 验证查询详情返回账号信息，不存在的账号返回 404。
     */
    @Test
    @DisplayName("查询管理员详情并处理不存在的账号")
    void shouldQueryAdminDetail() {
        Long adminId = fixture().createAdmin("m5-detail-admin", PASSWORD, "enabled");

        String detail = assertOk(exchange(HttpMethod.GET, ADMIN_PATH + "/" + adminId, null, superToken()));
        assertThat(detail).contains("\"username\":\"m5-detail-admin\"");
        assertThat(detail).contains("\"status\":\"enabled\"");

        assertCode(exchange(HttpMethod.GET, ADMIN_PATH + "/999999", null, superToken()), 404);
    }

    /**
     * 验证修改接口只改显示名称，并让详情随之变化。
     */
    @Test
    @DisplayName("修改显示名称生效")
    void shouldUpdateDisplayName() {
        Long adminId = fixture().createAdmin("m5-rename-admin", PASSWORD, "enabled");
        String body = "{\"displayName\":\"重命名后的显示名称\"}";

        String updated = assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + adminId, body, superToken()));

        assertThat(updated).contains("\"displayName\":\"重命名后的显示名称\"");
        assertThat(assertOk(exchange(HttpMethod.GET, ADMIN_PATH + "/" + adminId, null, superToken())))
                .contains("\"displayName\":\"重命名后的显示名称\"");
    }

    /**
     * 验证停用账号后其既有令牌访问受保护接口立即返回 401，且账号无法再登录。
     */
    @Test
    @DisplayName("停用账号后既有令牌立即失效")
    void shouldInvalidateTokenAfterAdminDisabled() {
        Long adminId = fixture().createAdmin("m5-disable-admin", PASSWORD, "enabled");
        Long roleId = fixture().createRole("m5_disable_role", "停用角色");
        fixture().grantRole(adminId, roleId);
        fixture().linkRoleMenu(roleId, probeMenuId());
        String token = login("m5-disable-admin", PASSWORD);
        assertOk(exchange(HttpMethod.GET, "/api/admin/v1/m3/probe/permission", null, token));

        String disabled = assertOk(exchange(HttpMethod.PATCH, ADMIN_PATH + "/" + adminId + "/status",
                "{\"status\":\"disabled\"}", superToken()));

        assertThat(disabled).contains("\"status\":\"disabled\"");
        assertCode(exchange(HttpMethod.GET, "/api/admin/v1/m3/probe/permission", null, token), 401);
        assertCode(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token), 401);
        assertCode(exchange(HttpMethod.POST, "/api/admin/v1/auth/login",
                "{\"username\":\"m5-disable-admin\",\"password\":\"" + PASSWORD + "\"}", null), 401);
    }

    /**
     * 验证不能停用当前登录的管理员账号，返回 400 且账号仍是启用状态。
     */
    @Test
    @DisplayName("停用当前登录账号返回 400")
    void shouldRejectDisablingCurrentAdmin() {
        ResponseSnapshot login = superAdminLogin();
        String token = tokenOf(login);
        String adminId = idOf(assertOk(login));

        String rejected = assertCode(exchange(HttpMethod.PATCH, ADMIN_PATH + "/" + adminId + "/status",
                "{\"status\":\"disabled\"}", token), 400);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_admin where id = ? and status = ?", Long.valueOf(adminId),
                "enabled")).isEqualTo(1);
    }

    /**
     * 验证账号状态已经一致时返回 409，而不是报告修改成功。
     */
    @Test
    @DisplayName("状态已经一致返回 409")
    void shouldRejectStatusAlreadyApplied() {
        Long adminId = fixture().createAdmin("m5-status-admin", PASSWORD, "enabled");

        // 状态条件更新在受影响行数为 0 时返回 409：提交与当前状态一致不会报告成功
        assertCode(exchange(HttpMethod.PATCH, ADMIN_PATH + "/" + adminId + "/status", "{\"status\":\"enabled\"}",
                superToken()), 409);
    }

    /**
     * 验证重置密码后旧令牌与旧密码都失效，新密码可以登录。
     */
    @Test
    @DisplayName("重置密码使旧令牌失效且新密码可登录")
    void shouldResetPasswordAndRevokeTokens() {
        Long adminId = fixture().createAdmin("m5-password-admin", PASSWORD, "enabled");
        String oldToken = login("m5-password-admin", PASSWORD);
        String newPassword = "forge-new-pass-2026";

        String reset = assertOk(exchange(HttpMethod.PATCH, ADMIN_PATH + "/" + adminId + "/password",
                "{\"newPassword\":\"" + newPassword + "\"}", superToken()));

        assertThat(reset).contains("\"data\":null");
        assertThat(reset).doesNotContain(newPassword);
        assertCode(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, oldToken), 401);
        assertCode(exchange(HttpMethod.POST, "/api/admin/v1/auth/login",
                "{\"username\":\"m5-password-admin\",\"password\":\"" + PASSWORD + "\"}", null), 401);
        assertThat(login("m5-password-admin", newPassword)).isNotBlank();
    }

    /**
     * 验证角色分配为全量替换：重复提交同一角色结果一致且不产生重复关系。
     */
    @Test
    @DisplayName("角色全量替换可重复提交且不产生重复关系")
    void shouldReplaceAdminRolesIdempotently() {
        Long adminId = fixture().createAdmin("m5-role-admin", PASSWORD, "enabled");
        Long firstRoleId = fixture().createRole("m5_role_first", "第一个角色");
        Long secondRoleId = fixture().createRole("m5_role_second", "第二个角色");
        fixture().grantRole(adminId, firstRoleId);
        String body = "{\"roleIds\":[\"" + firstRoleId + "\",\"" + secondRoleId + "\"]}";

        String first = assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + adminId + "/roles", body, superToken()));
        assertThat(idsOf(first, "roles")).containsExactlyInAnyOrder(String.valueOf(firstRoleId),
                String.valueOf(secondRoleId));

        String second = assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + adminId + "/roles", body, superToken()));
        assertThat(idsOf(second, "roles")).containsExactlyInAnyOrder(String.valueOf(firstRoleId),
                String.valueOf(secondRoleId));
        assertThat(countOf("select count(*) from sys_admin_role where admin_id = ? and role_id = ?", adminId,
                firstRoleId)).isEqualTo(1);
        assertThat(countOf("select count(*) from sys_admin_role where admin_id = ?", adminId)).isEqualTo(2);

        String emptied = assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + adminId + "/roles",
                "{\"roleIds\":[]}", superToken()));
        assertThat(idsOf(emptied, "roles")).isEmpty();
        assertThat(countOf("select count(*) from sys_admin_role where admin_id = ?", adminId)).isZero();
    }

    /**
     * 验证管理员列表既受分页上限约束，又受权限代码约束。
     */
    @Test
    @DisplayName("分页越界与缺少查看权限分别返回 400 与 403")
    void shouldRejectOversizedPageAndMissingPermission() {
        assertCode(exchange(HttpMethod.GET, ADMIN_PATH + "?pageSize=101", null, superToken()), 400);

        Long roleId = fixture().createRole("m5_limited_role", "受限角色");
        Long adminId = fixture().createAdmin(PLAIN_ADMIN_USERNAME, PASSWORD, "enabled");
        fixture().grantRole(adminId, roleId);
        String token = login(PLAIN_ADMIN_USERNAME, PASSWORD);

        String denied = assertCode(exchange(HttpMethod.GET, ADMIN_PATH, null, token), 403);

        assertThat(denied).contains("\"data\":null");
        assertThat(denied).doesNotContain(SUPER_ADMIN_USERNAME);
    }

    /**
     * 验证未携带令牌访问管理接口返回 401。
     */
    @Test
    @DisplayName("未携带令牌访问管理员列表返回 401")
    void shouldRejectAdminListWithoutToken() {
        String unauth = assertCode(exchange(HttpMethod.GET, ADMIN_PATH, null, null), 401);

        assertThat(unauth).contains("\"data\":null");
    }

    /**
     * 验证页码小于 1 返回 400。
     */
    @Test
    @DisplayName("页码为 0 返回 400")
    void shouldRejectZeroPageNum() {
        assertCode(exchange(HttpMethod.GET, ADMIN_PATH + "?pageNum=0", null, superToken()), 400);
    }
}

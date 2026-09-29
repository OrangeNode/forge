package cn.orangenode.forge.m5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

/**
 * 角色、菜单与权限管理接口的端到端验证。
 *
 * <p>三类主数据共用同一条安全约定：新增或修改后立即可查，被引用时阻止删除并返回 409，
 * 未被引用时逻辑删除成功且列表不再包含。授权接口为全量替换，重复提交不会留下重复关系。</p>
 *
 * <p>断言同时覆盖 HTTP 状态与 {@code body.code}，删除后的可见性通过再次调用列表接口确认，
 * 而不是只看删除响应的成功码。</p>
 */
class RoleMenuPermissionIntegrationTest extends M5ManagementTestSupport {

    /**
     * 验证新增角色可同时提交权限与菜单，详情按 ID 回显授权明细。
     */
    @Test
    @DisplayName("新增角色返回权限与菜单授权明细")
    void shouldCreateRoleWithGrants() {
        Long permissionId = fixture().createPermission("system:probe:update", "探针修改");
        Long menuId = fixture().createMenu(0L, "工作台", "m5-workbench", 1);
        String body = "{\"code\":\"M5_OPS_ROLE\",\"name\":\"运维角色\",\"description\":\"说明\",\"sortNo\":5,"
                + "\"permissionIds\":[\"" + permissionId + "\"],\"menuIds\":[\"" + menuId + "\"]}";

        String created = assertOk(exchange(HttpMethod.POST, ROLE_PATH, body, superToken()));

        assertThat(created).contains("\"code\":\"m5_ops_role\"");
        assertThat(arrayOf(created, "permissionIds")).containsExactly(String.valueOf(permissionId));
        assertThat(arrayOf(created, "menuIds")).containsExactly(String.valueOf(menuId));
        String roleId = idOf(created);

        String detail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(detail).contains("\"name\":\"运维角色\"");
        assertThat(arrayOf(detail, "permissionIds")).containsExactly(String.valueOf(permissionId));
        assertThat(arrayOf(detail, "menuIds")).containsExactly(String.valueOf(menuId));

        String page = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "?pageSize=10", null, superToken()));
        assertThat(page).contains("\"code\":\"m5_ops_role\"");
        assertThat(page).contains("\"pageNum\":1");
    }

    /**
     * 验证角色基础信息可修改，权限与菜单授权为全量替换。
     */
    @Test
    @DisplayName("修改角色并全量替换授权")
    void shouldUpdateRoleAndReplaceGrants() {
        Long roleId = fixture().createRole("m5_update_role", "待修改角色");
        Long firstPermissionId = probePermissionId();
        Long secondPermissionId = fixture().createPermission("system:probe:update", "探针修改");
        Long menuId = fixture().createMenu(0L, "概览", "m5-home", 1);
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"permissionIds\":[\"" + firstPermissionId + "\"],\"menuIds\":[]}", superToken()));

        String updated = assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId,
                "{\"code\":\"m5_update_role\",\"name\":\"已修改角色\",\"description\":\"新说明\",\"sortNo\":9}",
                superToken()));
        assertThat(updated).contains("\"name\":\"已修改角色\"");
        assertThat(updated).contains("\"description\":\"新说明\"");

        String granted = "{\"permissionIds\":[\"" + secondPermissionId + "\",\"" + secondPermissionId
                + "\"],\"menuIds\":[\"" + menuId + "\"]}";
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants", granted, superToken()));
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants", granted, superToken()));

        String detail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(arrayOf(detail, "permissionIds")).containsExactly(String.valueOf(secondPermissionId));
        assertThat(arrayOf(detail, "menuIds")).containsExactly(String.valueOf(menuId));
        assertThat(countOf("select count(*) from sys_role_permission where role_id = ?", roleId)).isEqualTo(1);
        assertThat(countOf("select count(*) from sys_role_menu where role_id = ?", roleId)).isEqualTo(1);
    }

    /**
     * 验证被管理员引用的角色不允许删除。
     */
    @Test
    @DisplayName("被管理员引用的角色删除返回 409")
    void shouldRejectDeletingReferencedRole() {
        Long roleId = fixture().createRole("m5_referenced_role", "被引用角色");
        Long adminId = fixture().createAdmin(PLAIN_ADMIN_USERNAME, PASSWORD, "enabled");
        fixture().grantRole(adminId, roleId);

        String rejected = assertCode(exchange(HttpMethod.DELETE, ROLE_PATH + "/" + roleId, null, superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_role where id = ? and deleted = 0", roleId)).isEqualTo(1);
    }

    /**
     * 验证内置超级管理员角色不允许删除，即使它没有被任何管理员引用。
     */
    @Test
    @DisplayName("内置超级管理员角色删除返回 409")
    void shouldRejectDeletingSuperRole() {
        Long superRoleId = builtInSuperRoleId();

        String rejected = assertCode(exchange(HttpMethod.DELETE, ROLE_PATH + "/" + superRoleId, null, superToken()),
                409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_role where id = ? and deleted = 0", superRoleId)).isEqualTo(1);
    }

    /**
     * 验证未被引用的角色删除成功后从列表消失。
     */
    @Test
    @DisplayName("未引用角色删除成功后列表不再包含")
    void shouldDeleteUnreferencedRole() {
        Long roleId = fixture().createRole("m5_free_role", "可删除角色");

        String deleted = assertOk(exchange(HttpMethod.DELETE, ROLE_PATH + "/" + roleId, null, superToken()));

        assertThat(deleted).contains("\"data\":null");
        String page = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "?pageSize=100", null, superToken()));
        assertThat(page).doesNotContain("m5_free_role");
        assertThat(countOf("select count(*) from sys_role where id = ? and deleted = 0", roleId)).isZero();
    }

    /**
     * 验证菜单可用于目录节点，并验证路由标识重复返回 409。
     */
    @Test
    @DisplayName("新增菜单并拒绝重复路由标识")
    void shouldCreateMenuAndRejectDuplicateRouteKey() {
        String created = assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"工作台\",\"routeKey\":\"m5-menu-page\",\"sortNo\":1}", superToken()));
        assertThat(created).contains("\"routeKey\":\"m5-menu-page\"");
        assertThat(created).contains("\"parentId\":\"0\"");

        String rejected = assertCode(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"重复标识\",\"routeKey\":\"m5-menu-page\",\"sortNo\":2}", superToken()),
                409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-menu-page")).isEqualTo(1);

        String directory = assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"系统管理\",\"routeKey\":null,\"sortNo\":3}", superToken()));
        assertThat(directory).contains("\"name\":\"系统管理\"");

        String tree = assertOk(exchange(HttpMethod.GET, MENU_PATH, null, superToken()));
        assertThat(tree).contains("m5-menu-page");
    }

    /**
     * 验证菜单可修改，并且有子菜单的父菜单不允许删除。
     */
    @Test
    @DisplayName("修改菜单并拒绝删除有子菜单的父菜单")
    void shouldUpdateMenuAndRejectDeletingParent() {
        Long parentId = fixture().createMenu(0L, "系统管理", null, 1);
        Long childId = fixture().createMenu(parentId, "管理员账号", "m5-child-page", 1);

        String updated = assertOk(exchange(HttpMethod.PUT, MENU_PATH + "/" + childId,
                "{\"parentId\":\"" + parentId + "\",\"name\":\"管理员列表\",\"routeKey\":\"m5-child-page\","
                        + "\"sortNo\":2}", superToken()));
        assertThat(updated).contains("\"name\":\"管理员列表\"");
        assertThat(updated).contains("\"sortNo\":2");

        String rejected = assertCode(exchange(HttpMethod.DELETE, MENU_PATH + "/" + parentId, null, superToken()),
                409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_menu where id = ? and deleted = 0", parentId)).isEqualTo(1);
        assertOk(exchange(HttpMethod.DELETE, MENU_PATH + "/" + childId, null, superToken()));
        assertOk(exchange(HttpMethod.DELETE, MENU_PATH + "/" + parentId, null, superToken()));
        assertThat(countOf("select count(*) from sys_menu where id = ? and deleted = 0", parentId)).isZero();
    }

    /**
     * 验证被角色授予可见性的菜单不允许删除，解除授权后可删除。
     */
    @Test
    @DisplayName("被角色引用的菜单删除返回 409")
    void shouldRejectDeletingMenuReferencedByRole() {
        Long menuId = fixture().createMenu(0L, "权限管理", "m5-referenced-menu", 1);
        Long roleId = fixture().createRole("m5_menu_role", "菜单可见性角色");
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"permissionIds\":[],\"menuIds\":[\"" + menuId + "\"]}", superToken()));

        String rejected = assertCode(exchange(HttpMethod.DELETE, MENU_PATH + "/" + menuId, null, superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"permissionIds\":[],\"menuIds\":[]}", superToken()));
        assertOk(exchange(HttpMethod.DELETE, MENU_PATH + "/" + menuId, null, superToken()));
        assertThat(countOf("select count(*) from sys_menu where id = ? and deleted = 0", menuId)).isZero();
    }

    /**
     * 验证权限代码格式非法返回 400，且提示与字段名都指向 code。
     */
    @Test
    @DisplayName("权限代码格式非法返回 400")
    void shouldRejectInvalidPermissionCode() {
        String rejected = assertCode(exchange(HttpMethod.POST, PERMISSION_PATH,
                "{\"code\":\"System:Admin\",\"name\":\"格式错误\",\"description\":\"说明\"}", superToken()), 400);

        assertThat(rejected).contains("\"fieldErrors\"");
        assertThat(rejected).contains("\"field\":\"code\"");
        assertThat(countOf("select count(*) from sys_permission where code = ?", "System:Admin")).isZero();
    }

    /**
     * 验证权限代码重复返回 409。
     */
    @Test
    @DisplayName("权限代码重复返回 409")
    void shouldRejectDuplicatePermissionCode() {
        String body = "{\"code\":\"system:probe:view\",\"name\":\"重复权限\",\"description\":\"说明\"}";

        String rejected = assertCode(exchange(HttpMethod.POST, PERMISSION_PATH, body, superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_permission where code = ?", "system:probe:view")).isEqualTo(1);
    }

    /**
     * 验证修改权限名称与说明后可按关键字查询到，权限代码保持不变。
     */
    @Test
    @DisplayName("修改权限名称与说明")
    void shouldUpdatePermissionNameAndDescription() {
        Long permissionId = fixture().createPermission("system:probe:update", "探针修改");

        String updated = assertOk(exchange(HttpMethod.PUT, PERMISSION_PATH + "/" + permissionId,
                "{\"name\":\"探针变更\",\"description\":\"修改后的说明\"}", superToken()));

        assertThat(updated).contains("\"code\":\"system:probe:update\"");
        assertThat(updated).contains("\"name\":\"探针变更\"");
        assertThat(updated).contains("\"description\":\"修改后的说明\"");
        String page = assertOk(exchange(HttpMethod.GET, PERMISSION_PATH + "?code=system:probe:update", null,
                superToken()));
        assertThat(page).contains("\"name\":\"探针变更\"");
    }

    /**
     * 验证被角色引用的权限不允许删除，未被引用时删除成功并从列表消失。
     */
    @Test
    @DisplayName("被引用的权限返回 409，未引用的权限删除成功")
    void shouldRejectReferencedPermissionAndDeleteFreeOne() {
        Long referencedId = fixture().createPermission("system:probe:update", "探针修改");
        Long freeId = fixture().createPermission("system:probe:grant", "探针授权");
        Long roleId = fixture().createRole("m5_permission_role", "权限引用角色");
        linkRolePermission(roleId, referencedId);

        String rejected = assertCode(exchange(HttpMethod.DELETE, PERMISSION_PATH + "/" + referencedId, null,
                superToken()), 409);
        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_permission where id = ? and deleted = 0", referencedId))
                .isEqualTo(1);

        String deleted = assertOk(exchange(HttpMethod.DELETE, PERMISSION_PATH + "/" + freeId, null, superToken()));
        assertThat(deleted).contains("\"data\":null");
        String page = assertOk(exchange(HttpMethod.GET, PERMISSION_PATH + "?pageSize=100", null, superToken()));
        assertThat(page).doesNotContain("system:probe:grant");
        assertThat(countOf("select count(*) from sys_permission where id = ? and deleted = 0", freeId)).isZero();
    }
}

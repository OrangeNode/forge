package cn.orangenode.forge.m5;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import cn.orangenode.forge.m3.M3TestSupport;

/**
 * 角色、菜单与权限配置管理接口的端到端验证。
 *
 * <p>角色、菜单与权限三类数据共用同一条安全约定：新增或修改后立即可查，被引用时阻止删除并返回 409，
 * 未被引用时逻辑删除成功且列表不再包含。角色授权为全量替换，重复提交不会留下重复关系。
 * 权限标识不再是独立主数据：它作为菜单节点上的配置维护，角色授予菜单节点即获得该节点声明的接口权限，
 * 因此权限维护用例全部打向菜单接口，而不是已经不存在的 {@code /system/permissions}。</p>
 *
 * <p>断言同时覆盖 HTTP 状态与 {@code body.code}，删除后的可见性通过再次调用列表接口确认，
 * 而不是只看删除响应的成功码；权限变更是否真的生效则通过受权限保护的探针接口观察
 * “先 403、再 200、再 403”的真实结果。</p>
 */
class RoleMenuPermissionIntegrationTest extends M5ManagementTestSupport {

    /**
     * 验证新增角色可同时提交菜单授权，详情按 ID 回显授权明细与推导出的权限标识。
     */
    @Test
    @DisplayName("新增角色返回菜单授权与推导出的权限标识")
    void shouldCreateRoleWithGrants() {
        Long menuId = fixture().createMenuWithPermissions(0L, "工作台", "m5-workbench", "system:probe:update", 1);
        String body = "{\"code\":\"M5_OPS_ROLE\",\"name\":\"运维角色\",\"description\":\"说明\",\"sortNo\":5,"
                + "\"menuIds\":[\"" + menuId + "\"]}";

        String created = assertOk(exchange(HttpMethod.POST, ROLE_PATH, body, superToken()));

        assertThat(created).contains("\"code\":\"m5_ops_role\"");
        assertThat(arrayOf(created, "permissionCodes")).containsExactly("system:probe:update");
        assertThat(arrayOf(created, "menuIds")).containsExactly(String.valueOf(menuId));
        String roleId = idOf(created);

        String detail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(detail).contains("\"name\":\"运维角色\"");
        assertThat(arrayOf(detail, "permissionCodes")).containsExactly("system:probe:update");
        assertThat(arrayOf(detail, "menuIds")).containsExactly(String.valueOf(menuId));

        String page = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "?pageSize=10", null, superToken()));
        assertThat(page).contains("\"code\":\"m5_ops_role\"");
        assertThat(page).contains("\"pageNum\":1");
    }

    /**
     * 验证角色基础信息可修改，菜单授权为全量替换且重复提交不产生重复关系。
     */
    @Test
    @DisplayName("修改角色并全量替换授权")
    void shouldUpdateRoleAndReplaceGrants() {
        Long roleId = fixture().createRole("m5_update_role", "待修改角色");
        Long firstMenuId = fixture().createMenuWithPermissions(0L, "探针查看页", "m5-probe-view-page",
                "system:probe:view", 1);
        Long secondMenuId = fixture().createMenuWithPermissions(0L, "探针修改页", "m5-probe-update-page",
                "system:probe:update", 2);
        Long visibilityMenuId = fixture().createMenu(0L, "概览", "m5-home", 3);
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"menuIds\":[\"" + firstMenuId + "\"]}", superToken()));

        String updated = assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId,
                "{\"code\":\"m5_update_role\",\"name\":\"已修改角色\",\"description\":\"新说明\",\"sortNo\":9}",
                superToken()));
        assertThat(updated).contains("\"name\":\"已修改角色\"");
        assertThat(updated).contains("\"description\":\"新说明\"");

        String granted = "{\"menuIds\":[\"" + secondMenuId + "\",\"" + secondMenuId + "\",\""
                + visibilityMenuId + "\"]}";
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants", granted, superToken()));
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants", granted, superToken()));

        String detail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(arrayOf(detail, "permissionCodes")).containsExactly("system:probe:update");
        assertThat(arrayOf(detail, "menuIds")).containsExactlyInAnyOrder(String.valueOf(secondMenuId),
                String.valueOf(visibilityMenuId));
        assertThat(countOf("select count(*) from sys_role_menu where role_id = ?", roleId)).isEqualTo(2);
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
     * 验证新增菜单可同时声明权限中文元数据、类型与图标，并验证路由标识重复返回 409。
     */
    @Test
    @DisplayName("新增菜单并拒绝重复路由标识")
    void shouldCreateMenuAndRejectDuplicateRouteKey() {
        String created = assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"工作台\",\"routeKey\":\"m5-menu-page\","
                        + "\"menuType\":\"page\",\"icon\":\"dashboard\","
                        + "\"permissions\":[{\"code\":\"system:probe:update\","
                        + "\"name\":\"更新探针配置\",\"description\":\"修改系统探针参数\"}],\"sortNo\":1}",
                superToken()));
        assertThat(created).contains("\"routeKey\":\"m5-menu-page\"");
        assertThat(created).contains("\"parentId\":\"0\"");
        assertThat(created).contains("\"menuType\":\"page\"");
        assertThat(created).contains("\"icon\":\"dashboard\"");
        assertThat(created).contains("\"code\":\"system:probe:update\"");
        assertThat(created).contains("\"name\":\"更新探针配置\"");
        assertThat(created).contains("\"description\":\"修改系统探针参数\"");

        String rejected = assertCode(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"重复标识\",\"routeKey\":\"m5-menu-page\",\"sortNo\":2}", superToken()),
                409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-menu-page")).isEqualTo(1);

        String directory = assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"系统管理\",\"menuType\":\"directory\","
                        + "\"icon\":\"settings\",\"routeKey\":null,\"sortNo\":3}", superToken()));
        assertThat(directory).contains("\"name\":\"系统管理\"");
        assertThat(directory).contains("\"menuType\":\"directory\"");

        String tree = assertOk(exchange(HttpMethod.GET, MENU_PATH, null, superToken()));
        assertThat(tree).contains("m5-menu-page");
    }

    /**
     * 验证菜单可修改权限标识，并且有子菜单的父菜单不允许删除。
     */
    @Test
    @DisplayName("修改菜单权限标识并拒绝删除有子菜单的父菜单")
    void shouldUpdateMenuAndRejectDeletingParent() {
        Long parentId = fixture().createMenu(0L, "系统管理", null, 1);
        Long childId = fixture().createMenu(parentId, "管理员账号", "m5-child-page", 1);

        String updated = assertOk(exchange(HttpMethod.PUT, MENU_PATH + "/" + childId,
                "{\"parentId\":\"" + parentId + "\",\"name\":\"管理员列表\",\"routeKey\":\"m5-child-page\","
                        + "\"permCodes\":[\"system:probe:update\"],\"sortNo\":2}", superToken()));
        assertThat(updated).contains("\"name\":\"管理员列表\"");
        assertThat(updated).contains("\"sortNo\":2");
        assertThat(updated).contains("\"code\":\"system:probe:update\"");

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
                "{\"menuIds\":[\"" + menuId + "\"]}", superToken()));

        String rejected = assertCode(exchange(HttpMethod.DELETE, MENU_PATH + "/" + menuId, null, superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"menuIds\":[]}", superToken()));
        assertOk(exchange(HttpMethod.DELETE, MENU_PATH + "/" + menuId, null, superToken()));
        assertThat(countOf("select count(*) from sys_menu where id = ? and deleted = 0", menuId)).isZero();
    }

    /**
     * 验证菜单声明的权限代码格式非法时返回 400，并在 {@code data.fieldErrors} 中指出 permCodes 字段。
     *
     * <p>格式规则由请求体上的 {@code @PermissionCodeElements} 约束表达，
     * 因此与其它字段校验走同一条出口，前端可以把提示直接显示在权限编辑区域；
     * 错误信息不回显被拒绝的原值。</p>
     */
    @Test
    @DisplayName("菜单权限代码格式非法返回 400 并指出 permCodes 字段")
    void shouldRejectInvalidPermissionCodeFormat() {
        String rejected = assertCode(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"格式错误\",\"routeKey\":\"m5-invalid-code\","
                        + "\"permCodes\":[\"System:Admin\"],\"sortNo\":1}", superToken()), 400);

        assertThat(rejected).contains("\"fieldErrors\"");
        assertThat(rejected).contains("\"field\":\"permCodes\"");
        assertThat(rejected).contains("模块:资源:动作");
        assertThat(rejected).doesNotContain("System:Admin");
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-invalid-code")).isZero();
    }

    /**
     * 验证单个菜单声明的权限代码数量超过上限时返回 400，并在 {@code data.fieldErrors} 中指出字段。
     */
    @Test
    @DisplayName("菜单权限代码数量超限返回 400 并指出 permCodes 字段")
    void shouldRejectTooManyPermissionCodes() {
        StringBuilder codes = new StringBuilder();
        for (int index = 0; index < 51; index++) {
            codes.append(index == 0 ? "" : ",").append("\"m5:limit:code").append(index).append('"');
        }

        String rejected = assertCode(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"超出数量\",\"routeKey\":\"m5-too-many-codes\","
                        + "\"permCodes\":[" + codes + "],\"sortNo\":1}", superToken()), 400);

        assertThat(rejected).contains("\"fieldErrors\"");
        assertThat(rejected).contains("\"field\":\"permCodes\"");
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-too-many-codes")).isZero();
    }

    /**
     * 验证权限代码在全局唯一：已被其他菜单节点声明时新增返回 409，且不写入新节点。
     */
    @Test
    @DisplayName("权限代码已被其他菜单声明返回 409")
    void shouldRejectPermissionCodeDeclaredByAnotherMenu() {
        assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"首个声明\",\"routeKey\":\"m5-code-owner\","
                        + "\"permCodes\":[\"system:probe:update\"],\"sortNo\":1}", superToken()));

        String rejected = assertCode(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"重复声明\",\"routeKey\":\"m5-code-duplicate\","
                        + "\"permCodes\":[\"system:probe:update\"],\"sortNo\":2}", superToken()), 409);

        assertThat(rejected).contains("\"data\":null");
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-code-duplicate")).isZero();
        assertThat(countOf("select count(*) from sys_menu where route_key = ?", "m5-code-owner")).isEqualTo(1);
    }

    /**
     * 验证修改菜单节点声明的权限标识会立即改变已授权角色能调用的接口。
     *
     * <p>被修改的节点是夹具建立的集成测试权限节点：它同时声明探针权限与全部管理接口权限，
     * 因此可以只摘掉探针权限而保留其余声明，超级管理员仍能继续调用菜单与角色接口。
     * 角色对该节点有授权，摘掉探针权限后下一个请求立即 403，重新声明后立即恢复 200，
     * 角色详情推导出的权限标识也同步变化。</p>
     */
    @Test
    @DisplayName("修改菜单权限标识后已授权角色立即失去或恢复访问")
    void shouldApplyMenuPermissionChangeImmediately() {
        Long menuId = probeMenuId();
        Long roleId = fixture().createRole("m5_menu_perm_role", "菜单权限角色");
        Long adminId = fixture().createAdmin(PLAIN_ADMIN_USERNAME, PASSWORD, "enabled");
        fixture().grantRole(adminId, roleId);
        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + roleId + "/grants",
                "{\"menuIds\":[\"" + menuId + "\"]}", superToken()));
        String token = login(PLAIN_ADMIN_USERNAME, PASSWORD);
        assertOk(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token));

        List<String> originCodes = List.of(menuPermissionCodeText(menuId).split(","));
        List<String> withoutProbe = originCodes.stream()
                .filter(code -> !M3TestSupport.PROBE_PERMISSION.equals(code))
                .toList();
        assertOk(exchange(HttpMethod.PUT, MENU_PATH + "/" + menuId,
                menuUpdateBody(menuId, withoutProbe), superToken()));

        assertCode(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token), 403);
        String withoutProbeDetail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(arrayOf(withoutProbeDetail, "permissionCodes")).doesNotContain(M3TestSupport.PROBE_PERMISSION);

        assertOk(exchange(HttpMethod.PUT, MENU_PATH + "/" + menuId,
                menuUpdateBody(menuId, originCodes), superToken()));

        assertOk(exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token));
        String restoredDetail = assertOk(exchange(HttpMethod.GET, ROLE_PATH + "/" + roleId, null, superToken()));
        assertThat(arrayOf(restoredDetail, "permissionCodes")).contains(M3TestSupport.PROBE_PERMISSION);
    }
}

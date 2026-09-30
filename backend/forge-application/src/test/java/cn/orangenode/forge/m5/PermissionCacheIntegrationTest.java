package cn.orangenode.forge.m5;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import cn.orangenode.forge.framework.config.ForgeSecurityProperties;
import cn.orangenode.forge.framework.redis.ForgeRedisTemplate;
import cn.orangenode.forge.m3.M3TestSupport;

/**
 * 权限缓存与失效的端到端验证。
 *
 * <p>这是 M5 的重点用例：普通管理员第一次访问受权限保护的探针返回 403，随后只通过管理接口
 * （角色授权或管理员角色分配）授予权限，不等待、不手动清缓存，下一次请求必须立即返回 200；
 * 再通过管理接口收回权限，下一次请求必须立即恢复 403。</p>
 *
 * <p>用例同时断言缓存键与全局权限版本键的真实变化，证明“立即生效”来自服务层提交后的版本递增，
 * 而不是恰好没有缓存或缓存存活时间到期。</p>
 */
class PermissionCacheIntegrationTest extends M5ManagementTestSupport {

    /**
     * 全局权限版本的逻辑键名，与实现约定一致。
     */
    private static final String PERMISSION_VERSION_KEY = "auth:perm-version";

    /**
     * 权限解析服务使用的 Redis 入口，用于断言键前缀与存活时间。
     */
    @Autowired
    private ForgeRedisTemplate forgeRedisTemplate;

    /**
     * 认证与权限配置，缓存存活时间以配置为唯一来源。
     */
    @Autowired
    private ForgeSecurityProperties securityProperties;

    /**
     * 验证角色授权变更提交后权限缓存立即失效。
     *
     * <p>授权以菜单为单位：授予承载探针权限的菜单节点后角色立即获得该权限，
     * 提交空菜单集合则立即收回。</p>
     */
    @Test
    @DisplayName("角色授权变更后权限立即生效")
    void shouldApplyRoleGrantImmediately() {
        Scope scope = preparePlainAdminWithoutProbePermission();
        String token = login(PLAIN_ADMIN_USERNAME, PASSWORD);

        assertCode(probe(token), 403);
        assertCode(probe(token), 403);
        assertThat(redisDouble().keys()).anyMatch(key -> key.startsWith(forgeRedisTemplate.getKeyPrefix()
                + ":auth:perm:v0:"));

        String granted = assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + scope.roleId() + "/grants",
                "{\"menuIds\":[\"" + probeMenuId() + "\"]}", superToken()));

        assertThat(granted).contains("\"data\":null");
        assertOk(probe(token));

        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + scope.roleId() + "/grants",
                "{\"menuIds\":[]}", superToken()));
        assertCode(probe(token), 403);
    }

    /**
     * 验证给管理员分配含该权限的角色后权限立即生效。
     */
    @Test
    @DisplayName("管理员角色分配后权限立即生效")
    void shouldApplyAdminRoleChangeImmediately() {
        Scope scope = preparePlainAdminWithoutProbePermission();
        String token = login(PLAIN_ADMIN_USERNAME, PASSWORD);
        assertCode(probe(token), 403);

        Long grantedRoleId = fixture().createRole("m5_granted_role", "探针授权角色");
        linkRoleMenu(grantedRoleId, probeMenuId());
        assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + scope.adminId() + "/roles",
                "{\"roleIds\":[\"" + grantedRoleId + "\"]}", superToken()));

        assertOk(probe(token));

        assertOk(exchange(HttpMethod.PUT, ADMIN_PATH + "/" + scope.adminId() + "/roles",
                "{\"roleIds\":[\"" + scope.roleId() + "\"]}", superToken()));
        assertCode(probe(token), 403);
    }

    /**
     * 验证新增菜单节点声明的权限同样会改变超级管理员的权限集合，证明缓存失效不只挂在授权关系上。
     */
    @Test
    @DisplayName("超级管理员权限集合随菜单声明权限立即变化")
    void shouldRefreshSuperAdminPermissionsAfterMenuPermissionCreated() {
        String token = superToken();

        List<String> before = arrayOf(assertOk(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token)),
                "permissionCodes");
        assertThat(before).contains(M3TestSupport.PROBE_PERMISSION);
        assertThat(before).doesNotContain("system:probe:grant");

        assertOk(exchange(HttpMethod.POST, MENU_PATH,
                "{\"parentId\":\"0\",\"name\":\"探针授权页面\",\"routeKey\":\"m5-probe-grant-page\","
                        + "\"permCodes\":[\"system:probe:grant\"],\"sortNo\":9}", superToken()));

        List<String> after = arrayOf(assertOk(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, token)),
                "permissionCodes");
        assertThat(after).contains(M3TestSupport.PROBE_PERMISSION);
        assertThat(after).contains("system:probe:grant");
    }

    /**
     * 验证每次管理接口变更都会递增全局权限版本，并带正数存活时间。
     */
    @Test
    @DisplayName("管理接口变更递增全局权限版本")
    void shouldIncrementPermissionVersionOnManagementChanges() {
        Scope scope = preparePlainAdminWithoutProbePermission();
        String versionKey = forgeRedisTemplate.key(PERMISSION_VERSION_KEY);
        assertThat(redisDouble().value(versionKey)).isNull();

        assertOk(exchange(HttpMethod.PUT, ROLE_PATH + "/" + scope.roleId() + "/grants",
                "{\"menuIds\":[\"" + probeMenuId() + "\"]}", superToken()));

        assertThat(redisDouble().value(versionKey)).isEqualTo("1");
        assertThat(redisDouble().ttl(versionKey))
                .isEqualTo(Duration.ofSeconds(Math.max(securityProperties.getPermissionCacheTtlSeconds(),
                        securityProperties.getTokenTtlSeconds())));
        // 版本递增只让旧键失效；新键在下一次解析权限时写入，因此这里先触发一次身份查询
        assertOk(exchange(HttpMethod.GET, "/api/admin/v1/auth/me", null, superToken()));
        assertThat(redisDouble().keys())
                .anyMatch(key -> key.startsWith(forgeRedisTemplate.getKeyPrefix() + ":auth:perm:v1:"));
    }

    /**
     * 构造一个不含探针权限的普通角色与普通管理员，并完成登录前的准备。
     *
     * @return 普通管理员与角色的 ID
     */
    private Scope preparePlainAdminWithoutProbePermission() {
        Long roleId = fixture().createRole(PLAIN_ROLE_CODE, "普通角色");
        Long adminId = fixture().createAdmin(PLAIN_ADMIN_USERNAME, PASSWORD, "enabled");
        fixture().grantRole(adminId, roleId);
        return new Scope(adminId, roleId);
    }

    /**
     * 以指定令牌访问受探针权限保护的接口。
     *
     * @param token 访问令牌
     * @return 响应快照
     */
    private ResponseSnapshot probe(String token) {
        return exchange(HttpMethod.GET, M3TestSupport.PERMISSION_PROBE, null, token);
    }

    /**
     * 普通管理员与角色的 ID 组合。
     *
     * @param adminId 管理员 ID
     * @param roleId  角色 ID
     */
    private record Scope(Long adminId, Long roleId) {
    }
}

package cn.orangenode.forge.m3;

import org.springframework.jdbc.core.JdbcTemplate;

import cn.orangenode.forge.support.RuntimeSchema;

/**
 * M3 端到端验证共用的测试约定。
 *
 * <p>表结构由 {@link RuntimeSchema} 统一维护，避免各处测试夹具与迁移脚本各写一份列定义。
 * 本类只保留 M3 用例共享的常量与建表入口。</p>
 *
 * <p>本类只提供夹具，不参与生产代码；管理员凭据是测试固定值，不是任何环境的真实口令。</p>
 */
public final class M3TestSupport {

    /**
     * 受权限保护的探针接口路径。
     */
    public static final String PERMISSION_PROBE = "/api/admin/v1/m3/probe/permission";

    /**
     * 只要求认证的探针接口路径，用于读取当前认证主体。
     */
    public static final String PRINCIPAL_PROBE = "/api/admin/v1/m3/probe/principal";

    /**
     * 测试管理员用户名。
     */
    public static final String ADMIN_USERNAME = "forge-admin";

    /**
     * 测试管理员密码，满足创建密码的长度规则。
     */
    public static final String ADMIN_PASSWORD = "forge-test-pass";

    /**
     * 测试管理员角色代码，与超级管理员角色代码刻意不同，便于分别验证两种语义。
     */
    public static final String ROLE_CODE = "ops_admin";

    /**
     * 测试管理员显示名称。
     */
    public static final String ADMIN_DISPLAY_NAME = "测试管理员";

    /**
     * 探针权限代码。
     */
    public static final String PROBE_PERMISSION = "system:probe:view";

    /**
     * 另一个权限代码，用于验证权限是按代码而非“有任意权限”放行。
     */
    public static final String OTHER_PERMISSION = "system:probe:update";

    /**
     * 工具类不允许实例化。
     */
    private M3TestSupport() {
    }

    /**
     * 重建系统表与审计日志表结构。
     *
     * <p>登录用例在成功与失败路径都会写登录日志，因此认证相关用例需要审计表存在。</p>
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateSystemTables(JdbcTemplate jdbcTemplate) {
        RuntimeSchema.recreateSystemAndAuditTables(jdbcTemplate);
    }
}

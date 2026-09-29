package cn.orangenode.forge.m3;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * M3 端到端验证共用的测试约定与内存库结构。
 *
 * <p>H2 结构按 MySQL 迁移脚本的列与约束手工对齐：Flyway 脚本包含 MySQL 专有的
 * 存储引擎、字符集与注释语法，不能直接在 H2 上执行，因此测试单独建表。
 * 真实 MySQL 的迁移由本机联调执行 Flyway 验证，两者不一致时以迁移脚本为准。</p>
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
     * 重建系统表结构，保证用例之间互不影响。
     *
     * <p>按关系表、主数据的顺序删除，再按主数据、关系表的顺序创建。</p>
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateSystemTables(JdbcTemplate jdbcTemplate) {
        for (String table : List.of("sys_role_permission", "sys_role_menu", "sys_admin_role", "sys_permission",
                "sys_menu", "sys_role", "sys_admin")) {
            jdbcTemplate.execute("drop table if exists " + table);
        }
        jdbcTemplate.execute("""
                create table sys_admin (
                  id bigint not null auto_increment,
                  username varchar(64) not null,
                  password_hash varchar(100) not null,
                  display_name varchar(64) not null,
                  status varchar(16) not null,
                  last_login_at datetime(3) null,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint uk_sys_admin_username unique (username)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_role (
                  id bigint not null auto_increment,
                  code varchar(64) not null,
                  name varchar(64) not null,
                  description varchar(255) null,
                  sort_no int not null default 0,
                  deleted tinyint not null default 0,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint uk_sys_role_code unique (code)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_menu (
                  id bigint not null auto_increment,
                  parent_id bigint not null default 0,
                  name varchar(64) not null,
                  route_key varchar(64) null,
                  sort_no int not null default 0,
                  deleted tinyint not null default 0,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint uk_sys_menu_route_key unique (route_key)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_permission (
                  id bigint not null auto_increment,
                  code varchar(128) not null,
                  name varchar(64) not null,
                  description varchar(255) null,
                  deleted tinyint not null default 0,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint uk_sys_permission_code unique (code)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_admin_role (
                  admin_id bigint not null,
                  role_id bigint not null,
                  created_at datetime(3) not null,
                  created_by bigint null,
                  primary key (admin_id, role_id),
                  constraint fk_sys_admin_role_admin foreign key (admin_id) references sys_admin (id),
                  constraint fk_sys_admin_role_role foreign key (role_id) references sys_role (id)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_role_menu (
                  role_id bigint not null,
                  menu_id bigint not null,
                  created_at datetime(3) not null,
                  created_by bigint null,
                  primary key (role_id, menu_id),
                  constraint fk_sys_role_menu_role foreign key (role_id) references sys_role (id),
                  constraint fk_sys_role_menu_menu foreign key (menu_id) references sys_menu (id)
                )
                """);
        jdbcTemplate.execute("""
                create table sys_role_permission (
                  role_id bigint not null,
                  permission_id bigint not null,
                  created_at datetime(3) not null,
                  created_by bigint null,
                  primary key (role_id, permission_id),
                  constraint fk_sys_role_permission_role foreign key (role_id) references sys_role (id),
                  constraint fk_sys_role_permission_permission foreign key (permission_id)
                    references sys_permission (id)
                )
                """);
    }
}

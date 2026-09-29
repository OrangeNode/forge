package cn.orangenode.forge.support;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 集成测试用的内存库结构。
 *
 * <p>Flyway 脚本使用 MySQL 专有的存储引擎、字符集与注释语法，不能在 H2 上执行，
 * 因此测试单独建表；列名与约束以对应的迁移脚本为准，真实 MySQL 的迁移在联调时单独验证。</p>
 *
 * <p>所有方法先删后建，保证用例之间互不影响。调用方按需要选择建哪些表：
 * 认证与权限用例需要系统表与审计表，文件用例还需要文件表。</p>
 */
public final class RuntimeSchema {

    /**
     * 工具类不允许实例化。
     */
    private RuntimeSchema() {
    }

    /**
     * 种入当前全部接口使用的权限代码。
     *
     * <p>与迁移脚本中的权限种子保持一致：超级管理员按“全部有效权限”解析，
     * 因此内存库如果不种入这些权限行，超级管理员也会因为数据库里没有对应权限而被拒绝。</p>
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void seedPermissions(JdbcTemplate jdbcTemplate) {
        for (String code : PERMISSION_CODES) {
            jdbcTemplate.update("insert into sys_permission (code, name, deleted, created_at, updated_at) "
                    + "values (?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", code, code);
        }
    }

    /**
     * 当前全部接口使用的权限代码，与迁移脚本的种子一致，另含认证探针使用的测试权限。
     */
    private static final List<String> PERMISSION_CODES = List.of(
            "system:admin:view", "system:admin:create", "system:admin:update", "system:admin:status",
            "system:admin:password", "system:admin:role",
            "system:role:view", "system:role:create", "system:role:update", "system:role:delete",
            "system:role:grant",
            "system:menu:view", "system:menu:create", "system:menu:update", "system:menu:delete",
            "system:permission:view", "system:permission:create", "system:permission:update",
            "system:permission:delete",
            "file:record:view", "file:record:upload", "file:record:download", "file:record:delete",
            "file:storage:view", "file:storage:create", "file:storage:update", "file:storage:delete",
            "file:storage:test", "file:storage:default",
            "audit:login:view", "audit:operation:view",
            "system:probe:view");

    /**
     * 重建系统管理与审计日志表。
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateSystemAndAuditTables(JdbcTemplate jdbcTemplate) {
        recreateSystemTables(jdbcTemplate);
        recreateAuditTables(jdbcTemplate);
    }

    /**
     * 重建管理员、角色、菜单、权限及关系表。
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

    /**
     * 重建审计日志表。
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateAuditTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("drop table if exists audit_operation_log");
        jdbcTemplate.execute("drop table if exists audit_login_log");
        jdbcTemplate.execute("""
                create table audit_login_log (
                  id bigint not null auto_increment,
                  username varchar(64) not null,
                  result varchar(16) not null,
                  reason varchar(64) null,
                  client_ip varchar(45) null,
                  trace_id varchar(64) null,
                  created_at datetime(3) not null,
                  primary key (id)
                )
                """);
        jdbcTemplate.execute("""
                create table audit_operation_log (
                  id bigint not null auto_increment,
                  operator_type varchar(16) not null,
                  operator_id bigint null,
                  operator_name varchar(64) null,
                  action varchar(64) not null,
                  resource_type varchar(64) null,
                  resource_id varchar(64) null,
                  result_code int not null,
                  trace_id varchar(64) null,
                  created_at datetime(3) not null,
                  primary key (id)
                )
                """);
    }

    /**
     * 重建文件存储配置、默认指针与文件记录表。
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateFileTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("drop table if exists file_record");
        jdbcTemplate.execute("drop table if exists file_storage_default");
        jdbcTemplate.execute("drop table if exists file_storage_config");
        jdbcTemplate.execute("""
                create table file_storage_config (
                  id bigint not null auto_increment,
                  code varchar(64) not null,
                  version int not null,
                  name varchar(64) not null,
                  provider varchar(16) not null,
                  base_dir varchar(255) null,
                  endpoint varchar(255) null,
                  region varchar(64) null,
                  bucket varchar(128) null,
                  path_style tinyint not null default 1,
                  access_key varchar(512) null,
                  secret_key varchar(1024) null,
                  max_file_size bigint not null,
                  allowed_extensions varchar(512) null,
                  deleted tinyint not null default 0,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint uk_file_storage_config_code_version unique (code, version)
                )
                """);
        jdbcTemplate.execute("""
                create table file_storage_default (
                  id bigint not null,
                  config_id bigint not null,
                  updated_at datetime(3) not null,
                  updated_by bigint null,
                  primary key (id),
                  constraint fk_file_storage_default_config foreign key (config_id)
                    references file_storage_config (id)
                )
                """);
        jdbcTemplate.execute("""
                create table file_record (
                  id bigint not null auto_increment,
                  storage_config_id bigint not null,
                  object_key varchar(512) not null,
                  original_name varchar(255) not null,
                  content_type varchar(128) null,
                  size_bytes bigint not null,
                  uploader_id bigint not null,
                  uploader_name varchar(64) null,
                  deleted tinyint not null default 0,
                  created_at datetime(3) not null,
                  updated_at datetime(3) not null,
                  created_by bigint null,
                  updated_by bigint null,
                  primary key (id),
                  constraint fk_file_record_config foreign key (storage_config_id)
                    references file_storage_config (id)
                )
                """);
    }
}

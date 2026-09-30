package cn.orangenode.forge.support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
     * 种入当前全部接口使用的权限标识。
     *
     * <p>权限标识挂在菜单节点上（{@code sys_menu.perm_codes}），角色授予菜单节点即获得该节点声明的权限。
     * 内存库如果不把全部接口的权限标识挂到菜单上，超级管理员也会因为数据库里没有对应权限而被拒绝，
     * 因此这里为全部标识建出若干菜单节点。</p>
     *
     * <p>标识按 {@code sys_menu.perm_codes} 的列宽分成多组：全部代码拼成一行会超过 512 个字符，
     * 真实环境的做法也是每个页面节点只声明该页面的若干权限。第一组固定包含探针权限，
     * 其路由标识为 {@link #PERMISSION_MENU_ROUTE_KEY}，供 M5 用例按标识定位。</p>
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void seedPermissions(JdbcTemplate jdbcTemplate) {
        List<List<String>> groups = permissionCodeGroups();
        for (int index = 0; index < groups.size(); index++) {
            jdbcTemplate.update("insert into sys_menu (parent_id, name, route_key, perm_codes, sort_no, deleted, "
                    + "created_at, updated_at) values (0, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                    index == 0 ? "集成测试权限节点" : "集成测试权限节点 " + (index + 1),
                    index == 0 ? PERMISSION_MENU_ROUTE_KEY : null, String.join(",", groups.get(index)), index);
        }
    }

    /**
     * 按列宽把全部权限标识分成若干组。
     *
     * <p>按顺序累加长度，超过列宽就另起一组；单组内容保证不超过 {@code sys_menu.perm_codes} 的列宽。</p>
     *
     * @return 权限标识分组，每组可直接写入一个菜单节点
     */
    private static List<List<String>> permissionCodeGroups() {
        List<List<String>> groups = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int length = 0;
        for (String code : permissionCodeOrder()) {
            int added = current.isEmpty() ? code.length() : code.length() + 1;
            if (length + added > PERMISSION_CODES_MAX_LENGTH) {
                groups.add(List.copyOf(current));
                current.clear();
                length = 0;
                added = code.length();
            }
            current.add(code);
            length += added;
        }
        if (!current.isEmpty()) {
            groups.add(List.copyOf(current));
        }
        return groups;
    }

    /**
     * 权限标识的种子顺序：探针权限固定在首位，其余按字典序排列，保证每次运行的分组结果一致。
     *
     * @return 用于分组的权限标识顺序
     */
    private static List<String> permissionCodeOrder() {
        List<String> ordered = new ArrayList<>();
        ordered.add(PROBE_PERMISSION_CODE);
        PERMISSION_CODES.stream().filter(code -> !PROBE_PERMISSION_CODE.equals(code)).sorted().forEach(ordered::add);
        return ordered;
    }

    /**
     * 集成测试权限节点的路由标识，便于用例按标识定位声明探针权限的节点。
     */
    public static final String PERMISSION_MENU_ROUTE_KEY = "test-permissions";

    /**
     * 探针权限代码，必须落在 {@link #PERMISSION_MENU_ROUTE_KEY} 指向的节点上：
     * M5 的权限缓存用例通过授予该节点让普通管理员获得探针权限。
     */
    private static final String PROBE_PERMISSION_CODE = "system:probe:view";

    /**
     * 权限标识列的长度上限，与 {@code sys_menu.perm_codes} 的列宽一致。
     */
    private static final int PERMISSION_CODES_MAX_LENGTH = 512;

    /**
     * 权限代码到中文展示名称的映射，键集合即全部接口使用的权限标识。
     */
    private static final Map<String, String> PERMISSION_NAMES = Map.ofEntries(
            Map.entry("system:admin:view", "查询管理员"),
            Map.entry("system:admin:create", "新增管理员"),
            Map.entry("system:admin:update", "修改管理员"),
            Map.entry("system:admin:status", "启停管理员"),
            Map.entry("system:admin:password", "重置管理员密码"),
            Map.entry("system:admin:role", "分配管理员角色"),
            Map.entry("system:role:view", "查询角色"),
            Map.entry("system:role:create", "新增角色"),
            Map.entry("system:role:update", "修改角色"),
            Map.entry("system:role:delete", "删除角色"),
            Map.entry("system:role:grant", "角色授权"),
            Map.entry("system:menu:view", "查询菜单"),
            Map.entry("system:menu:create", "新增菜单"),
            Map.entry("system:menu:update", "修改菜单"),
            Map.entry("system:menu:delete", "删除菜单"),
            Map.entry("file:record:view", "查询文件"),
            Map.entry("file:record:upload", "上传文件"),
            Map.entry("file:record:download", "下载文件"),
            Map.entry("file:record:delete", "删除文件"),
            Map.entry("file:storage:view", "查询存储配置"),
            Map.entry("file:storage:create", "新增存储配置"),
            Map.entry("file:storage:update", "修改存储配置"),
            Map.entry("file:storage:delete", "删除存储配置"),
            Map.entry("file:storage:test", "检测存储连接"),
            Map.entry("file:storage:default", "切换默认存储"),
            Map.entry("audit:login:view", "查询登录日志"),
            Map.entry("audit:operation:view", "查询操作日志"),
            Map.entry("system:probe:view", "集成测试探针权限"));

    /**
     * 当前全部接口使用的权限代码，与迁移脚本的种子一致，另含认证探针使用的测试权限。
     */
    private static final List<String> PERMISSION_CODES = List.copyOf(PERMISSION_NAMES.keySet());

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
     * 重建管理员、角色、菜单及关系表。
     *
     * <p>与 M5 改造后的结构一致：角色授权只有 {@code sys_role_menu} 一张关系表，
     * 权限代码与中文资料均保存在 {@code sys_menu} 上，不存在独立权限表。</p>
     *
     * @param jdbcTemplate 路由数据源上的 JDBC 模板
     */
    public static void recreateSystemTables(JdbcTemplate jdbcTemplate) {
        for (String table : List.of("sys_role_menu", "sys_admin_role",
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
                  menu_type varchar(16) not null default 'page',
                  icon varchar(32) null,
                  route_key varchar(64) null,
                  perm_codes varchar(512) null,
                  permissions_json varchar(8000) null,
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
                  constraint uk_sys_role_menu_menu_role unique (menu_id, role_id),
                  constraint fk_sys_role_menu_role foreign key (role_id) references sys_role (id),
                  constraint fk_sys_role_menu_menu foreign key (menu_id) references sys_menu (id)
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

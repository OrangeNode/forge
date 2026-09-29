-- M5 系统管理数据：菜单树与接口权限。
--
-- 约定（见 docs/conventions/数据库设计规范.md）：
--   1. 菜单只描述可见性，route_key 必须是前端本地路由白名单中的标识；
--   2. 权限代码为 模块:资源:动作，是 @PreAuthorize 的唯一依据；
--   3. 权限种子与所属模块迁移一起维护，不使用应用启动时无条件覆盖的方式；
--   4. 超级管理员角色由 forge.security.super-role-code 识别，拥有全部有效权限与菜单，
--      因此这里不需要为它建立 sys_role_permission 与 sys_role_menu 关系；
--   5. 已发布脚本不可修改，本脚本只做前向变更：M3 的“概览”菜单改名为“工作台”，
--      并使用派生表插入子菜单，避免同表自引用子查询。

UPDATE sys_menu SET name = '工作台' WHERE route_key = 'home';

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
VALUES (0, '系统管理', NULL, 10, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '管理员账号', 'system-admin', 10, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '系统管理' LIMIT 1) parent;

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '角色管理', 'system-role', 20, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '系统管理' LIMIT 1) parent;

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '菜单管理', 'system-menu', 30, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '系统管理' LIMIT 1) parent;

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '权限管理', 'system-permission', 40, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '系统管理' LIMIT 1) parent;

-- 管理员管理权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('system:admin:view', '查询管理员', '查询管理员列表与详情', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:admin:create', '新增管理员', '创建管理员账号', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:admin:update', '修改管理员', '修改管理员基础信息', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:admin:status', '启停管理员', '启用或停用管理员账号', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:admin:password', '重置管理员密码', '重置管理员登录密码并撤销其会话', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:admin:role', '分配管理员角色', '调整管理员拥有的角色', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 角色管理权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('system:role:view', '查询角色', '查询角色列表与详情', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:role:create', '新增角色', '创建角色', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:role:update', '修改角色', '修改角色基础信息', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:role:delete', '删除角色', '逻辑删除未被引用的角色', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:role:grant', '角色授权', '维护角色的菜单与权限关系', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 菜单管理权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('system:menu:view', '查询菜单', '查询菜单树', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:menu:create', '新增菜单', '创建菜单节点', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:menu:update', '修改菜单', '修改菜单节点', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:menu:delete', '删除菜单', '逻辑删除未被引用的菜单', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 权限管理权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('system:permission:view', '查询权限', '查询权限列表', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:permission:create', '新增权限', '创建权限代码', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:permission:update', '修改权限', '修改权限说明', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('system:permission:delete', '删除权限', '逻辑删除未被引用的权限', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

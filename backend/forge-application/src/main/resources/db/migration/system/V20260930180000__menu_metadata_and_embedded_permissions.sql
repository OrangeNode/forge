-- M5 界面与 RBAC 二次改造：菜单补充类型、图标，并把权限中文资料完整收进菜单行。
--
-- 目标结构只保留管理员、角色、菜单三张 RBAC 主表；角色通过 sys_role_menu 授予菜单，
-- 菜单的 perm_codes 供鉴权高效读取，permissions_json 保存代码、中文名称与说明供界面维护。
-- 本迁移先完整搬迁既有权限资料，再删除不再需要的 sys_permission，避免存量中文名称丢失。

ALTER TABLE sys_menu
    ADD COLUMN menu_type VARCHAR(16) NOT NULL DEFAULT 'page' COMMENT '菜单类型：directory 目录、page 页面' AFTER name,
    ADD COLUMN icon VARCHAR(32) NULL COMMENT '前端内置图标标识' AFTER menu_type,
    ADD COLUMN permissions_json JSON NULL COMMENT '权限代码、中文名称与说明的 JSON 数组' AFTER perm_codes;

UPDATE sys_menu SET menu_type = 'directory' WHERE route_key IS NULL;

UPDATE sys_menu menu
SET permissions_json = (
    SELECT JSON_ARRAYAGG(JSON_OBJECT(
            'code', permission.code,
            'name', permission.name,
            'description', permission.description
        ))
    FROM sys_permission permission
    WHERE FIND_IN_SET(permission.code, menu.perm_codes) > 0
)
WHERE menu.perm_codes IS NOT NULL AND menu.perm_codes <> '';

-- 既有菜单图标采用前端白名单中的稳定标识；未知业务菜单仍可在菜单管理页面选择图标。
UPDATE sys_menu SET icon = 'dashboard' WHERE route_key = 'home';
UPDATE sys_menu SET icon = 'settings' WHERE route_key IS NULL AND name = '系统管理';
UPDATE sys_menu SET icon = 'users', name = '用户管理' WHERE route_key = 'system-admin';
UPDATE sys_menu SET icon = 'shield' WHERE route_key = 'system-role';
UPDATE sys_menu SET icon = 'menu' WHERE route_key = 'system-menu';
UPDATE sys_menu SET icon = 'folder' WHERE route_key IS NULL AND name = '文件与存储';
UPDATE sys_menu SET icon = 'file' WHERE route_key = 'file-list';
UPDATE sys_menu SET icon = 'database' WHERE route_key = 'storage-config';
UPDATE sys_menu SET icon = 'activity' WHERE route_key IS NULL AND name = '审计中心';
UPDATE sys_menu SET icon = 'clipboard' WHERE route_key = 'audit-operation';
UPDATE sys_menu SET icon = 'login' WHERE route_key = 'audit-login';

DROP TABLE sys_permission;

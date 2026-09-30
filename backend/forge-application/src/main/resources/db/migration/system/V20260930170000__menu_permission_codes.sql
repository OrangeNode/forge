-- M5 改造：RBAC 收敛为管理员、角色、菜单三张主表，权限标识作为菜单节点的配置项。
--
-- 约定（见 docs/conventions/数据库设计规范.md）：
--   1. 已发布脚本不可修改，本脚本只做前向变更（V20260930130000 的权限种子按原样保留）；
--   2. 权限标识的归属与生效范围由菜单节点决定（sys_menu.perm_codes），角色授予菜单节点即同时获得
--      该节点声明的权限，因此角色授权只保留 sys_role_menu 一张关系表；
--   3. sys_permission 保留但语义收窄为“权限展示资料”：只存权限代码的名称与说明，供界面展示，
--      不参与授权判断；标识本身来自菜单节点，资料由菜单保存路径自动同步增删；
--   4. sys_role_menu 的主键 (role_id, menu_id) 之外加唯一键 (menu_id, role_id)，
--      让“按菜单查授权角色”也能走索引，与菜单删除前的引用检查一致。
--
-- 权限标识格式固定为 模块:资源:动作，大小写敏感（与 @PreAuthorize 的字符串完全一致），
-- 一个节点声明多个权限时用英文逗号分隔，不携带空格。

ALTER TABLE sys_menu
    ADD COLUMN perm_codes VARCHAR(512) NULL COMMENT '该节点声明的接口权限代码，多个用英文逗号分隔' AFTER route_key;

-- 权限标识从 sys_permission 迁到菜单节点：每个页面的权限都挂在该页面节点上，
-- 子功能（新增、修改、删除、授权）不单独建菜单节点，直接由所属页面的权限标识表达，避免树里出现不可见节点。
UPDATE sys_menu SET perm_codes = 'system:admin:view,system:admin:create,system:admin:update,system:admin:status,system:admin:password,system:admin:role'
WHERE route_key = 'system-admin';

UPDATE sys_menu SET perm_codes = 'system:role:view,system:role:create,system:role:update,system:role:delete,system:role:grant'
WHERE route_key = 'system-role';

UPDATE sys_menu SET perm_codes = 'system:menu:view,system:menu:create,system:menu:update,system:menu:delete'
WHERE route_key = 'system-menu';

UPDATE sys_menu SET perm_codes = 'file:record:view,file:record:upload,file:record:download,file:record:delete'
WHERE route_key = 'file-list';

UPDATE sys_menu SET perm_codes = 'file:storage:view,file:storage:create,file:storage:update,file:storage:delete,file:storage:test,file:storage:default'
WHERE route_key = 'storage-config';

UPDATE sys_menu SET perm_codes = 'audit:operation:view' WHERE route_key = 'audit-operation';
UPDATE sys_menu SET perm_codes = 'audit:login:view' WHERE route_key = 'audit-login';

-- 角色与权限关系表退出范围：授权入口只剩“角色与菜单”一张关系表。
DROP TABLE IF EXISTS sys_role_permission;

-- 权限展示资料重建：原表里已删除的“权限管理”权限（system:permission:*）不再被任何菜单声明，
-- 因此不保留；其余条目的中文名称与说明由原表内容迁移过来，界面不需要重新录入。
-- 必须先迁移再清空：声明表驱动迁移，只有当前菜单节点真正声明的标识才会进入资料表。
CREATE TEMPORARY TABLE tmp_permission_declared (
    code  VARCHAR(128) NOT NULL,
    name  VARCHAR(64)  NOT NULL,
    note  VARCHAR(255) NULL
);

INSERT INTO tmp_permission_declared (code, name, note) VALUES
    ('system:admin:view', '查询管理员', '查询管理员列表与详情'),
    ('system:admin:create', '新增管理员', '创建管理员账号'),
    ('system:admin:update', '修改管理员', '修改管理员基础信息'),
    ('system:admin:status', '启停管理员', '启用或停用管理员账号'),
    ('system:admin:password', '重置管理员密码', '重置管理员登录密码并撤销其会话'),
    ('system:admin:role', '分配管理员角色', '调整管理员拥有的角色'),
    ('system:role:view', '查询角色', '查询角色列表与详情'),
    ('system:role:create', '新增角色', '创建角色'),
    ('system:role:update', '修改角色', '修改角色基础信息'),
    ('system:role:delete', '删除角色', '逻辑删除未被引用的角色'),
    ('system:role:grant', '角色授权', '维护角色的菜单授权，接口权限由菜单节点推导'),
    ('system:menu:view', '查询菜单', '查询菜单树'),
    ('system:menu:create', '新增菜单', '创建菜单节点'),
    ('system:menu:update', '修改菜单', '修改菜单节点与其声明的权限标识'),
    ('system:menu:delete', '删除菜单', '逻辑删除未被引用的菜单'),
    ('file:record:view', '查询文件', '查询文件记录列表'),
    ('file:record:upload', '上传文件', '上传文件到默认存储方案'),
    ('file:record:download', '下载文件', '按文件 ID 下载'),
    ('file:record:delete', '删除文件', '逻辑删除文件记录'),
    ('file:storage:view', '查询存储配置', '查询存储方案版本'),
    ('file:storage:create', '新增存储配置', '创建存储方案版本'),
    ('file:storage:update', '修改存储配置', '修改存储方案并生成新版本'),
    ('file:storage:delete', '删除存储配置', '删除未被引用的存储方案版本'),
    ('file:storage:test', '检测存储连接', '检测存储方案连通性'),
    ('file:storage:default', '切换默认存储', '切换默认存储方案，只影响新上传'),
    ('audit:operation:view', '查询操作日志', '查询管理操作审计记录'),
    ('audit:login:view', '查询登录日志', '查询登录成功与失败记录');

-- 名称与说明优先沿用原表内容，原表没有的记录使用本脚本的默认文案。
UPDATE tmp_permission_declared declared
    JOIN sys_permission old ON old.code = declared.code
SET declared.name = old.name,
    declared.note = COALESCE(old.description, declared.note);

DELETE FROM sys_permission;

INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at)
SELECT code, name, note, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3) FROM tmp_permission_declared;

DROP TEMPORARY TABLE tmp_permission_declared;

-- “权限管理”页面退出范围：权限标识改为在「菜单管理」页面维护。
-- 该菜单节点被逻辑删除，route_key 仍占用唯一键（与数据库规范“默认不回收唯一业务键”一致）。
UPDATE sys_menu SET deleted = 1 WHERE route_key = 'system-permission';

-- 资料表按最新结构收紧：名称必填，说明可空；逻辑删除不再使用（资料随标识同步增删）。
ALTER TABLE sys_permission
    MODIFY COLUMN name VARCHAR(64) NOT NULL COMMENT '权限名称，仅用于界面展示';

ALTER TABLE sys_role_menu
    ADD UNIQUE KEY uk_sys_role_menu_menu_role (menu_id, role_id);

-- 数据修复：file_storage_config.name 的存量值在 M4 验收时被按单字节编码写入，
-- 落库内容已经是字面的问号（0x3F），无法从数据本身还原，这里按方案编码复原为中文名称。
UPDATE file_storage_config SET name = '本地默认存储' WHERE code = 'local-files' AND name = '?????????';
UPDATE file_storage_config SET name = 'MinIO 对象存储' WHERE code = 'minio' AND name = 'MinIO ??????';

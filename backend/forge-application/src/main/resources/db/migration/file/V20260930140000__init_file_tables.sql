-- M4 文件模块：存储配置、默认指针、文件记录，以及文件相关的菜单与权限。
--
-- 约定（见 docs/conventions/数据库设计规范.md）：
--   1. 存储配置按 (code, version) 版本化：目标（相对目录、endpoint、bucket）变化创建新版本，
--      文件记录引用上传时固定的版本 ID，因此切换默认方案不影响历史文件的读取；
--   2. 凭据使用 AES-GCM 加密后保存，主密钥只在环境变量中，接口与日志不回显；
--   3. 默认方案是固定单行指针（id = 1），只影响新上传；
--   4. 文件记录使用逻辑删除；对象本身是否清理由文件模块策略决定，不套用逻辑删除即视为对象已删除。

CREATE TABLE file_storage_config
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    code               VARCHAR(64)   NOT NULL COMMENT '存储方案代码，同代码下版本递增',
    version            INT           NOT NULL COMMENT '版本号，从 1 开始；目标变化时创建新版本',
    name               VARCHAR(64)   NOT NULL COMMENT '方案名称',
    provider           VARCHAR(16)   NOT NULL COMMENT '存储类型：local 本地文件系统，s3 兼容对象存储',
    base_dir           VARCHAR(255)  NULL COMMENT '本地存储相对目录（provider=local），必须位于环境根目录内',
    endpoint           VARCHAR(255)  NULL COMMENT '对象存储访问地址（provider=s3）',
    region             VARCHAR(64)   NULL COMMENT '对象存储区域（provider=s3）',
    bucket             VARCHAR(128)  NULL COMMENT '对象存储桶名称（provider=s3）',
    path_style         TINYINT       NOT NULL DEFAULT 1 COMMENT '是否使用 path-style 访问，MinIO 等兼容服务需要',
    access_key         VARCHAR(512)  NULL COMMENT '访问凭据密文（AES-GCM，provider=s3）',
    secret_key         VARCHAR(1024) NULL COMMENT '访问密钥密文（AES-GCM，provider=s3）',
    max_file_size      BIGINT        NOT NULL COMMENT '单文件大小上限（字节），不得高于应用硬上限',
    allowed_extensions VARCHAR(512)  NULL COMMENT '允许的扩展名，逗号分隔；为空表示不限制扩展名',
    deleted            TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_at         DATETIME(3)   NOT NULL COMMENT '创建时间（UTC）',
    updated_at         DATETIME(3)   NOT NULL COMMENT '更新时间（UTC）',
    created_by         BIGINT        NULL COMMENT '创建者管理员 ID',
    updated_by         BIGINT        NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_storage_config_code_version (code, version)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件存储配置（版本化）';

CREATE TABLE file_storage_default
(
    id         BIGINT      NOT NULL COMMENT '固定为 1 的单行指针',
    config_id  BIGINT      NOT NULL COMMENT '当前默认存储配置 ID',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    updated_by BIGINT      NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    CONSTRAINT fk_file_storage_default_config FOREIGN KEY (config_id) REFERENCES file_storage_config (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='默认存储配置指针，只影响新上传';

CREATE TABLE file_record
(
    id                BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    storage_config_id BIGINT       NOT NULL COMMENT '上传时固定的存储配置版本 ID',
    object_key        VARCHAR(512) NOT NULL COMMENT '存储对象键，随机生成，不包含原始文件名',
    original_name     VARCHAR(255) NOT NULL COMMENT '原始文件名，只保存名称部分',
    content_type      VARCHAR(128) NULL COMMENT '上传时声明的内容类型',
    size_bytes        BIGINT       NOT NULL COMMENT '文件大小（字节）',
    uploader_id       BIGINT       NOT NULL COMMENT '上传者管理员 ID',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_at        DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at        DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    created_by        BIGINT       NULL COMMENT '创建者管理员 ID',
    updated_by        BIGINT       NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    KEY idx_file_record_uploader (uploader_id, created_at),
    KEY idx_file_record_config (storage_config_id),
    CONSTRAINT fk_file_record_config FOREIGN KEY (storage_config_id) REFERENCES file_storage_config (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='文件元数据';

-- 文件模块菜单
INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
VALUES (0, '文件与存储', NULL, 20, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '文件列表', 'file-list', 10, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '文件与存储' LIMIT 1) parent;

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '存储配置', 'storage-config', 20, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '文件与存储' LIMIT 1) parent;

-- 文件模块权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('file:record:view', '查询文件', '查询文件列表与详情', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:record:upload', '上传文件', '上传文件到当前默认存储方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:record:download', '下载文件', '按文件 ID 下载文件', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:record:delete', '删除文件', '逻辑删除文件记录并清理对象', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:view', '查询存储配置', '查询存储方案与当前默认方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:create', '新增存储配置', '创建存储方案的新版本', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:update', '修改存储配置', '修改未被引用的存储方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:delete', '删除存储配置', '删除未被文件引用的存储方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:test', '检测存储连接', '对存储方案执行连接与读写检测', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('file:storage:default', '切换默认存储', '切换新上传使用的默认存储方案', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

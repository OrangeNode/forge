-- 文件公开外链（2026-10-08 用户要求“文件管理可以产生 apk 的外链”）：
-- 管理员为单个文件生成随机令牌后，任何人可凭 /api/public/v1/files/{令牌}/{文件名} 匿名下载；撤销即清空令牌。
-- 令牌只用于下载，不暴露文件 ID 与对象键。
ALTER TABLE file_record
    ADD COLUMN public_token VARCHAR(32) NULL COMMENT '公开外链令牌，为空表示未公开' AFTER uploader_name;

CREATE UNIQUE INDEX uk_file_record_public_token ON file_record (public_token);

UPDATE sys_menu
SET perm_codes = CONCAT(perm_codes, ',file:record:share'),
    permissions_json = JSON_ARRAY_APPEND(permissions_json, '$', JSON_OBJECT('code', 'file:record:share',
        'name', '公开外链', 'description', '为文件生成或撤销任何人可下载的公开外链')),
    updated_at = UTC_TIMESTAMP(3)
WHERE perm_codes LIKE '%file:record:view%' AND deleted = 0 AND perm_codes NOT LIKE '%file:record:share%';

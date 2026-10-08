-- 普通用户改为用户名登录（2026-10-08 用户要求）：不再使用邮箱，注册不经过邮箱验证码。
-- 用户名只允许字母、数字、下划线，4—20 位；表排序规则 utf8mb4_0900_ai_ci 使唯一键不区分大小写。
-- 现有账号用邮箱 @ 前面的部分清洗得到用户名：非字母数字下划线替换为下划线，不足 4 位补 _user，
-- 超长截断；与他人冲突时追加 _账号ID（仍超长时截断前缀）。

ALTER TABLE member_user ADD COLUMN username VARCHAR(20) NULL COMMENT '用户名，字母数字下划线 4-20 位，不区分大小写唯一' AFTER id;

UPDATE member_user
SET username = LEFT(RPAD(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9_]', '_'),
                         GREATEST(4, CHAR_LENGTH(REGEXP_REPLACE(SUBSTRING_INDEX(email, '@', 1), '[^A-Za-z0-9_]', '_'))), '_'), 20);

UPDATE member_user AS u
JOIN (
    SELECT id FROM (
        SELECT id, ROW_NUMBER() OVER (PARTITION BY LOWER(username) ORDER BY id) AS seq FROM member_user
    ) AS ranked WHERE seq > 1
) AS dup ON dup.id = u.id
SET u.username = CONCAT(LEFT(u.username, 19 - CHAR_LENGTH(u.id)), '_', u.id);

ALTER TABLE member_user MODIFY COLUMN username VARCHAR(20) NOT NULL COMMENT '用户名，字母数字下划线 4-20 位，不区分大小写唯一';
ALTER TABLE member_user ADD UNIQUE KEY uk_member_user_username (username);
ALTER TABLE member_user DROP INDEX uk_member_user_email;
ALTER TABLE member_user DROP COLUMN email;

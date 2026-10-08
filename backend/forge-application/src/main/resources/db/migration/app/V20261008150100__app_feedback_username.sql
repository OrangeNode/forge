-- 普通用户改为用户名登录：反馈中的提交人快照改为用户名。
ALTER TABLE app_feedback ADD COLUMN member_username VARCHAR(20) NULL COMMENT '提交人用户名快照' AFTER member_id;
UPDATE app_feedback AS f JOIN member_user AS u ON u.id = f.member_id SET f.member_username = u.username;
UPDATE app_feedback SET member_username = LEFT(SUBSTRING_INDEX(member_email, '@', 1), 20) WHERE member_username IS NULL;
ALTER TABLE app_feedback MODIFY COLUMN member_username VARCHAR(20) NOT NULL COMMENT '提交人用户名快照';
ALTER TABLE app_feedback DROP COLUMN member_email;

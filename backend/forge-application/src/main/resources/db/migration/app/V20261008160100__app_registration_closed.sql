-- 默认关闭公开注册，只能登录；账号由管理员在“会员管理”中新建（2026-10-08 用户要求）。
-- 需要重新开放时在“App 管理 → 功能开关”中打开。
UPDATE app_settings SET registration_open = 0, updated_at = UTC_TIMESTAMP(3) WHERE id = 1;

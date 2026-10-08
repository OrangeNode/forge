-- 关闭公开注册后由管理员新建普通用户（2026-10-08 用户要求“不开启注册，只能登录”）：
-- 在“会员管理 → 用户列表”页面上增加权限 member:user:create。
UPDATE sys_menu
SET perm_codes = CONCAT(perm_codes, ',member:user:create'),
    permissions_json = JSON_ARRAY_APPEND(permissions_json, '$', JSON_OBJECT('code', 'member:user:create',
        'name', '新建普通用户', 'description', '关闭公开注册后由管理员创建账号，可同时开通 VIP')),
    updated_at = UTC_TIMESTAMP(3)
WHERE route_key = 'member-user' AND deleted = 0 AND perm_codes NOT LIKE '%member:user:create%';

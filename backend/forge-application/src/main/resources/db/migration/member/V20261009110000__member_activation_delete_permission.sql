-- 激活码批量删除（2026-10-09 用户要求“批量作废、批量导出、批量删除”）：在“会员管理 → 激活码”页面上增加权限 member:activation:delete。
-- 导出复用查看权限，批量作废复用作废权限。
UPDATE sys_menu
SET perm_codes = CONCAT(perm_codes, ',member:activation:delete'),
    permissions_json = JSON_ARRAY_APPEND(permissions_json, '$', JSON_OBJECT('code', 'member:activation:delete',
        'name', '删除激活码', 'description', '批量删除未使用、已作废或已过期的激活码，已使用的码保留')),
    updated_at = UTC_TIMESTAMP(3)
WHERE route_key = 'member-activation' AND deleted = 0 AND perm_codes NOT LIKE '%member:activation:delete%';

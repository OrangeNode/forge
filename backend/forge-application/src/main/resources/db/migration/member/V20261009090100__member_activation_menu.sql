-- 会员管理 → 激活码页面与三个权限：查看、生成、作废。排在用户列表、登录设备之后。
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'激活码','page','key','member-activation','member:activation:view,member:activation:create,member:activation:revoke','[{"code":"member:activation:view","name":"查看激活码","description":"查询激活码、使用状态与使用账号"},{"code":"member:activation:create","name":"生成激活码","description":"批量生成注册激活码，可设置有效期与备注"},{"code":"member:activation:revoke","name":"作废激活码","description":"作废未使用的激活码，作废后不能再用于注册"}]',3,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id = 0 AND name = '会员管理' AND menu_type = 'directory' AND deleted = 0;

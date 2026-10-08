-- 会员管理菜单与权限：用户列表、登录设备两个页面。
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (0,'会员管理','directory','users',NULL,NULL,NULL,50,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
SET @member_menu_root = LAST_INSERT_ID();
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@member_menu_root,'用户列表','page','users','member-user','member:user:view,member:user:status,member:user:password,member:user:vip','[{"code":"member:user:view","name":"查看普通用户","description":"查询用户列表、详情与 VIP 记录"},{"code":"member:user:status","name":"启停普通用户","description":"停用后全部令牌立即失效"},{"code":"member:user:password","name":"重置普通用户密码","description":"重置后全部令牌立即失效"},{"code":"member:user:vip","name":"开通或延长 VIP","description":"手动延长、设置或取消 VIP 到期时间"}]',1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@member_menu_root,'登录设备','page','login','member-device','member:device:view,member:device:offline','[{"code":"member:device:view","name":"查看登录设备","description":"查看普通用户的登录设备与在线状态"},{"code":"member:device:offline","name":"强制下线","description":"撤销设备令牌或用户全部令牌"}]',2,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

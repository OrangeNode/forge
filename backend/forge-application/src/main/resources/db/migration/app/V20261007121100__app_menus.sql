-- App 管理菜单与权限：开屏设置、首页轮播、首页推荐位、热搜设置、功能开关五个页面。
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (0,'App 管理','directory','settings',NULL,NULL,NULL,60,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
SET @app_menu_root = LAST_INSERT_ID();
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@app_menu_root,'开屏设置','page','file','app-splash','app:splash:view,app:splash:edit,app:splash:delete','[{"code":"app:splash:view","name":"查看开屏设置","description":"查看开屏图、停留秒数与生效时间"},{"code":"app:splash:edit","name":"维护开屏设置","description":"新增与修改开屏"},{"code":"app:splash:delete","name":"删除开屏设置","description":"逻辑删除开屏"}]',1,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@app_menu_root,'首页轮播','page','dashboard','app-banner','app:banner:view,app:banner:edit,app:banner:delete','[{"code":"app:banner:view","name":"查看首页轮播","description":"查看轮播图片、跳转目标与上下线时间"},{"code":"app:banner:edit","name":"维护首页轮播","description":"新增与修改轮播"},{"code":"app:banner:delete","name":"删除首页轮播","description":"逻辑删除轮播"}]',2,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@app_menu_root,'首页推荐位','page','menu','app-section','app:section:view,app:section:edit,app:section:delete','[{"code":"app:section:view","name":"查看首页推荐位","description":"查看推荐行与挑片方式"},{"code":"app:section:edit","name":"维护首页推荐位","description":"新增与修改推荐行"},{"code":"app:section:delete","name":"删除首页推荐位","description":"逻辑删除推荐行"}]',3,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@app_menu_root,'热搜设置','page','activity','app-hot-search','app:hotsearch:view,app:hotsearch:edit','[{"code":"app:hotsearch:view","name":"查看热搜设置","description":"查看手动热搜词与屏蔽词"},{"code":"app:hotsearch:edit","name":"维护热搜设置","description":"修改手动热搜词与屏蔽词"}]',4,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
VALUES (@app_menu_root,'功能开关','page','shield','app-feature','app:feature:view,app:feature:edit','[{"code":"app:feature:view","name":"查看功能开关","description":"查看 VIP 开关、注册开关、同时登录台数与客服公告"},{"code":"app:feature:edit","name":"维护功能开关","description":"修改 VIP 开关、注册开关、同时登录台数与客服公告"}]',5,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

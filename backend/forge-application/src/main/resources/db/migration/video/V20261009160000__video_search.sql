-- 资源站搜索（2026-10-09 用户要求）：后台按关键词搜索单个或全部启用的资源站，勾选结果后采集入库。
-- 勾选采集仍是一条普通采集运行，只是按源站影片编号（ids）请求详情，入库规则与全量采集一致。
ALTER TABLE video_run ADD COLUMN upstream_ids VARCHAR(2048) NULL COMMENT '勾选采集的源站影片编号，逗号分隔；为空表示按范围与分类采集' AFTER upstream_category_id;

-- “视频管理 → 资源搜索”页面与两个权限：搜索、采集搜索结果。排在采集记录之后。
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'资源搜索','page','search','video-search','video:search:view,video:search:collect','[{"code":"video:search:view","name":"搜索资源站","description":"按关键词搜索单个或全部启用的资源站"},{"code":"video:search:collect","name":"采集搜索结果","description":"把勾选的搜索结果按现有采集规则入库"}]',7,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id = 0 AND name = '视频管理' AND menu_type = 'directory' AND deleted = 0;

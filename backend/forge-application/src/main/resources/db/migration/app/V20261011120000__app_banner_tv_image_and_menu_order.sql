-- 首页轮播增加电视端图片，并调整 App 管理菜单顺序（2026-10-11 用户要求）。
-- 电视端图片可为空：为空时电视端继续使用手机图片，已有轮播不受影响。
-- 后台把 App 管理的 9 个页面合并为 4 个标签页入口（前端按分组合并，菜单与角色授权仍按原页面登记），
-- 这里只调整排序，使合并后的入口依次为首页与开屏、公告与提示、功能与版本、反馈处理。

ALTER TABLE app_banner
    ADD COLUMN tv_image_file_id BIGINT NULL COMMENT '电视端图片文件主键，空表示使用手机图片' AFTER image_file_id;

UPDATE sys_menu SET sort_no=1, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-splash';
UPDATE sys_menu SET sort_no=2, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-banner';
UPDATE sys_menu SET sort_no=3, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-section';
UPDATE sys_menu SET sort_no=4, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-hot-search';
UPDATE sys_menu SET sort_no=5, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-announcement';
UPDATE sys_menu SET sort_no=6, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-marquee';
UPDATE sys_menu SET sort_no=7, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-feature';
UPDATE sys_menu SET sort_no=8, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-release';
UPDATE sys_menu SET sort_no=9, updated_at=UTC_TIMESTAMP(3) WHERE route_key='app-feedback';

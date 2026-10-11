-- App 公告弹窗与播放页滚动文字（2026-10-09 用户要求）。
-- 公告：进入首页后弹出，同一条公告每个用户只弹一次；标题、内容或按钮修改后 revision 加一，用户会再看到一次。
-- 内容为纯文本或 HTML，HTML 保存前按白名单过滤（只留常见排版、图片与链接，不含脚本与样式）。
-- 滚动文字：播放页线路选择上方循环滚动的一行提示，保存在单行全局设置中。

CREATE TABLE app_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    title VARCHAR(64) NOT NULL COMMENT '标题',
    content_type VARCHAR(8) NOT NULL COMMENT '内容格式 TEXT/HTML',
    content TEXT NOT NULL COMMENT '内容；HTML 为白名单过滤后的结果',
    revision INT NOT NULL DEFAULT 1 COMMENT '内容版本，标题、内容或按钮修改后加一',
    action_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '“去了解”按钮 NONE 不显示/VIDEO 影片/URL 外部链接',
    action_value VARCHAR(512) NULL COMMENT '跳转影片主键或外部链接',
    start_at DATETIME(3) NULL COMMENT '生效开始时间UTC，空表示立即',
    end_at DATETIME(3) NULL COMMENT '生效结束时间UTC，空表示长期',
    enabled TINYINT NOT NULL COMMENT '是否启用',
    sort_no INT NOT NULL COMMENT '优先级，越小越优先',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_app_announcement_active (deleted, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 公告';

CREATE TABLE app_announcement_read (
    member_id BIGINT NOT NULL COMMENT '普通用户主键（会员模块），不建外键',
    announcement_id BIGINT NOT NULL COMMENT '公告主键',
    revision INT NOT NULL COMMENT '已看过的内容版本',
    read_at DATETIME(3) NOT NULL COMMENT '最近关闭时间UTC',
    PRIMARY KEY (member_id, announcement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 公告已读记录';

ALTER TABLE app_settings
    ADD COLUMN marquee_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '播放页滚动文字是否启用';

ALTER TABLE app_settings
    ADD COLUMN marquee_text VARCHAR(200) NULL COMMENT '播放页滚动文字';

ALTER TABLE app_settings
    ADD COLUMN marquee_color VARCHAR(7) NULL COMMENT '滚动文字颜色 #RRGGBB';

UPDATE app_settings SET marquee_enabled=1, marquee_text='请勿相信视频中任何广告，视频中广告均为诈骗',
    marquee_color='#FF3B30' WHERE id=1;

-- “App 管理 → 公告管理”与“播放页滚动文字”页面与权限
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'公告管理','page','message','app-announcement','app:announcement:view,app:announcement:edit,app:announcement:delete','[{"code":"app:announcement:view","name":"查看公告","description":"查看 App 公告与生效时间"},{"code":"app:announcement:edit","name":"维护公告","description":"新增与修改 App 公告"},{"code":"app:announcement:delete","name":"删除公告","description":"逻辑删除 App 公告"}]',8,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id=0 AND name='App 管理' AND deleted=0;

INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'播放页滚动文字','page','activity','app-marquee','app:marquee:view,app:marquee:edit','[{"code":"app:marquee:view","name":"查看滚动文字","description":"查看播放页滚动文字、颜色与开关"},{"code":"app:marquee:edit","name":"修改滚动文字","description":"修改播放页滚动文字、颜色与开关"}]',9,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id=0 AND name='App 管理' AND deleted=0;

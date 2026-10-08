-- 开屏广告、首页轮播广告与 App 版本更新（2026-10-08 用户要求）。
-- 开屏可点击跳转影片或外部链接；轮播新增外部链接跳转；
-- 版本更新只有一行（手机与电视共用一个 APK）：版本号、版本名、包名与 SHA-256 由上传的 APK 自动解析，
-- App 低于“最低可用版本”时强制更新，介于最低与最新之间时提示可跳过的更新。

ALTER TABLE app_splash
    ADD COLUMN target_type VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT '点击跳转类型 NONE/VIDEO/URL' AFTER skippable;

ALTER TABLE app_splash
    ADD COLUMN target_value VARCHAR(512) NULL COMMENT '跳转影片主键或外部链接' AFTER target_type;

ALTER TABLE app_banner
    ADD COLUMN target_url VARCHAR(512) NULL COMMENT '跳转类型为 URL 时的外部链接' AFTER target_id;

CREATE TABLE app_release (
    id BIGINT NOT NULL COMMENT '固定为 1 的单行主键',
    version_code INT NULL COMMENT '最新版本号（Android versionCode），为空表示未发布',
    version_name VARCHAR(64) NULL COMMENT '最新版本名',
    min_version_code INT NULL COMMENT '最低可用版本号，低于该值强制更新；为空表示不强制',
    package_name VARCHAR(128) NULL COMMENT 'APK 包名',
    apk_file_id BIGINT NULL COMMENT 'APK 文件主键（文件模块）',
    apk_url VARCHAR(512) NULL COMMENT 'APK 公开下载路径',
    apk_sha256 CHAR(64) NULL COMMENT 'APK 的 SHA-256（小写十六进制）',
    apk_size BIGINT NULL COMMENT 'APK 字节数',
    changelog VARCHAR(1000) NULL COMMENT '更新说明',
    updated_by BIGINT NULL COMMENT '最后修改管理员主键',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 版本更新';

INSERT INTO app_release(id,created_at,updated_at) VALUES (1,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

-- “App 管理 → 版本更新”页面与权限
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'版本更新','page','refresh','app-release','app:release:view,app:release:edit','[{"code":"app:release:view","name":"查看版本更新","description":"查看当前发布的最新版本与最低可用版本"},{"code":"app:release:edit","name":"发布版本更新","description":"上传 APK 发布新版本、调整最低可用版本或撤销发布"}]',7,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id=0 AND name='App 管理' AND deleted=0;

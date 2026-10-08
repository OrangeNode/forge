-- App 设置表：开屏、首页轮播、首页推荐位与单行全局设置（功能开关、热搜、客服公告）。
-- 图片只保存文件模块的文件 ID；影片与分类只保存逻辑 ID，跨模块不建外键。

CREATE TABLE app_splash (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    name VARCHAR(64) NOT NULL COMMENT '名称，仅后台展示',
    image_file_id BIGINT NOT NULL COMMENT '封面图文件主键',
    duration_seconds INT NOT NULL COMMENT '停留秒数',
    skippable TINYINT NOT NULL COMMENT '是否可跳过',
    start_at DATETIME(3) NULL COMMENT '生效开始时间UTC，空表示立即',
    end_at DATETIME(3) NULL COMMENT '生效结束时间UTC，空表示长期',
    enabled TINYINT NOT NULL COMMENT '是否启用',
    sort_no INT NOT NULL COMMENT '优先顺序，越小越优先',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_app_splash_active (deleted, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 开屏';

CREATE TABLE app_banner (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    title VARCHAR(64) NOT NULL COMMENT '标题',
    image_file_id BIGINT NOT NULL COMMENT '图片文件主键',
    target_type VARCHAR(16) NOT NULL COMMENT '跳转类型 NONE/VIDEO/CATEGORY',
    target_id BIGINT NULL COMMENT '跳转影片或分类主键',
    start_at DATETIME(3) NULL COMMENT '上线时间UTC，空表示立即',
    end_at DATETIME(3) NULL COMMENT '下线时间UTC，空表示长期',
    enabled TINYINT NOT NULL COMMENT '是否启用',
    sort_no INT NOT NULL COMMENT '显示顺序',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_app_banner_active (deleted, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 首页轮播';

CREATE TABLE app_section (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    title VARCHAR(64) NOT NULL COMMENT '推荐行标题',
    source_type VARCHAR(16) NOT NULL COMMENT '数据来源 CATEGORY/MANUAL',
    category_id BIGINT NULL COMMENT '按分类自动取片时的分类主键',
    video_ids VARCHAR(2048) NULL COMMENT '手动挑片的影片主键，英文逗号分隔并保持顺序',
    item_limit INT NOT NULL COMMENT '最多展示条数',
    enabled TINYINT NOT NULL COMMENT '是否启用',
    sort_no INT NOT NULL COMMENT '显示顺序',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_app_section_active (deleted, enabled, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 首页推荐位';

CREATE TABLE app_settings (
    id BIGINT NOT NULL COMMENT '固定为 1 的单行主键',
    vip_required TINYINT NOT NULL COMMENT 'VIP 开关：开启时只有 VIP 能观看，关闭时登录即可观看',
    registration_open TINYINT NOT NULL COMMENT '是否开放注册',
    max_devices INT NOT NULL COMMENT '同一账号同时登录设备上限',
    service_text VARCHAR(512) NULL COMMENT '客服文字',
    notice_text VARCHAR(1024) NULL COMMENT '公告文字',
    hot_words TEXT NULL COMMENT '手动热搜词 JSON 数组',
    blocked_words TEXT NULL COMMENT '热搜屏蔽词 JSON 数组',
    updated_by BIGINT NULL COMMENT '最近修改管理员主键',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 全局设置';

INSERT INTO app_settings(id,vip_required,registration_open,max_devices,service_text,notice_text,hot_words,blocked_words,updated_by,created_at,updated_at)
VALUES (1,1,1,2,NULL,NULL,'[]','[]',NULL,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3));

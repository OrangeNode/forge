-- 视频采集业务表：不修改历史迁移，全部采用 InnoDB、utf8mb4。

CREATE TABLE video_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    parent_id BIGINT NULL COMMENT '父分类主键，顶级为空',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    sort_no INT NOT NULL COMMENT '显示顺序',
    enabled TINYINT NOT NULL COMMENT '启用状态',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频分类';

CREATE TABLE video_source (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    api_url VARCHAR(2048) NOT NULL COMMENT 'MacCMS JSON 接口地址',
    enabled TINYINT NOT NULL COMMENT '启用状态',
    source_zone VARCHAR(64) NOT NULL COMMENT '源站更新时间使用的时区',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_source_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频资源站';

CREATE TABLE video_source_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    source_id BIGINT NOT NULL COMMENT '资源站主键',
    upstream_id VARCHAR(128) NOT NULL COMMENT '源站记录编号',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    upstream_parent_id VARCHAR(128) NULL COMMENT '源站父分类编号',
    category_id BIGINT NULL COMMENT '本地分类主键',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_source_category_id (source_id, upstream_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频源分类映射';

CREATE TABLE video_video (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    match_name VARCHAR(512) NOT NULL COMMENT '规范化匹配片名',
    category_id BIGINT NOT NULL COMMENT '本地分类主键',
    year INT NULL COMMENT '年份，允许未知',
    area VARCHAR(255) NULL COMMENT '地区',
    language VARCHAR(255) NULL COMMENT '语言',
    director VARCHAR(2048) NULL COMMENT '导演人员文本',
    actors VARCHAR(4096) NULL COMMENT '演员人员文本',
    poster_url VARCHAR(4096) NULL COMMENT '远程封面地址',
    description TEXT NULL COMMENT '纯文本简介',
    remarks VARCHAR(512) NULL COMMENT '源站更新说明',
    declared_total INT NULL COMMENT '源站声明总集数，非实际地址数量',
    ended TINYINT NULL COMMENT '源站完结声明，允许未知',
    primary_origin_id BIGINT NULL COMMENT '主要来源记录主键',
    published TINYINT NOT NULL COMMENT '影片上架状态',
    metadata_locked TINYINT NOT NULL COMMENT '手工资料更新锁定',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_video_video_match (match_name(128)), KEY idx_video_video_category (category_id, published, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频影片';

CREATE TABLE video_origin (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    source_id BIGINT NOT NULL COMMENT '资源站主键',
    upstream_id VARCHAR(128) NOT NULL COMMENT '源站记录编号',
    video_id BIGINT NOT NULL COMMENT '本地影片主键',
    upstream_category_id VARCHAR(128) NULL COMMENT '源站分类编号',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    year INT NULL COMMENT '年份，允许未知',
    area VARCHAR(255) NULL COMMENT '地区',
    language VARCHAR(255) NULL COMMENT '语言',
    director VARCHAR(2048) NULL COMMENT '导演人员文本',
    actors VARCHAR(4096) NULL COMMENT '演员人员文本',
    source_updated_at DATETIME(3) NULL COMMENT '源站声明更新时间 UTC',
    collected_at DATETIME(3) NOT NULL COMMENT '最近采集时间 UTC',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_origin_identity (source_id, upstream_id), KEY idx_video_origin_video (video_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频来源记录';

CREATE TABLE video_play_line (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    origin_id BIGINT NOT NULL COMMENT '来源记录主键',
    code VARCHAR(255) NOT NULL COMMENT '原始播放线路标识',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    sort_no INT NOT NULL COMMENT '显示顺序',
    enabled TINYINT NOT NULL COMMENT '启用状态',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_play_line_origin (origin_id, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频播放线路';

CREATE TABLE video_episode (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    line_id BIGINT NOT NULL COMMENT '播放线路主键',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    sort_no INT NOT NULL COMMENT '显示顺序',
    url TEXT NOT NULL COMMENT '完整分集地址，保留参数及编码',
    url_type VARCHAR(255) NOT NULL COMMENT '媒体地址类型 M3U8 MP4 OTHER',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_episode_order (line_id, sort_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频分集';

CREATE TABLE video_settings (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    match_fields VARCHAR(512) NOT NULL COMMENT '全局严格去重字段列表',
    update_fields VARCHAR(1024) NOT NULL COMMENT '主要来源允许更新字段列表',
    auto_publish TINYINT NOT NULL COMMENT '新影片默认自动上架',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频采集设置';

CREATE TABLE video_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    name VARCHAR(255) NOT NULL COMMENT '名称',
    source_id BIGINT NOT NULL COMMENT '资源站主键',
    range_code VARCHAR(255) NOT NULL COMMENT '日周全部范围 DAY WEEK ALL',
    upstream_category_id VARCHAR(128) NULL COMMENT '源站分类编号',
    frequency VARCHAR(255) NOT NULL COMMENT '独立执行频率 INTERVAL DAILY WEEKLY',
    interval_hours INT NULL COMMENT '按小时执行间隔',
    weekday INT NULL COMMENT '周一至周日编号一至七',
    time_of_day VARCHAR(5) NOT NULL COMMENT '本地执行时间 HH:mm',
    zone_id VARCHAR(64) NOT NULL COMMENT '执行时区',
    enabled TINYINT NOT NULL COMMENT '启用状态',
    next_run_at DATETIME(3) NOT NULL COMMENT '下次执行时刻 UTC',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_video_task_due (enabled, next_run_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频定时任务';

CREATE TABLE video_run (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    task_id BIGINT NULL COMMENT '定时任务主键，手动运行为空',
    source_id BIGINT NOT NULL COMMENT '资源站主键',
    range_code VARCHAR(255) NOT NULL COMMENT '日周全部范围 DAY WEEK ALL',
    upstream_category_id VARCHAR(128) NULL COMMENT '源站分类编号',
    status VARCHAR(255) NOT NULL COMMENT '业务处理状态',
    next_page INT NOT NULL COMMENT '下次待处理页面断点',
    page_count INT NOT NULL COMMENT '源站返回总页数',
    processed BIGINT NOT NULL COMMENT '累计处理条数',
    inserted BIGINT NOT NULL COMMENT '累计新建影片数',
    updated BIGINT NOT NULL COMMENT '累计更新来源数',
    pending BIGINT NOT NULL COMMENT '累计存疑条数',
    skipped BIGINT NOT NULL COMMENT '累计跳过条数',
    error_message VARCHAR(1024) NULL COMMENT '失败原因，不含原始响应和凭据',
    warning VARCHAR(1024) NULL COMMENT '错过窗口提示',
    started_at DATETIME(3) NULL COMMENT '首次开始时间 UTC',
    finished_at DATETIME(3) NULL COMMENT '结束时间 UTC',
    settings_snapshot TEXT NOT NULL COMMENT '运行采用的采集规则快照 JSON',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_video_run_queue (status, id), KEY idx_video_run_source (source_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频采集运行';

CREATE TABLE video_pending (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    run_id BIGINT NULL COMMENT '关联运行，历史日志清理后可为空',
    source_id BIGINT NOT NULL COMMENT '资源站主键',
    upstream_id VARCHAR(128) NOT NULL COMMENT '源站记录编号',
    payload MEDIUMTEXT NOT NULL COMMENT '待确认采集数据快照 JSON',
    candidate_ids TEXT NOT NULL COMMENT '候选影片主键列表',
    reason VARCHAR(255) NOT NULL COMMENT '存疑原因',
    status VARCHAR(255) NOT NULL COMMENT '业务处理状态',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_video_pending_identity (source_id, upstream_id), KEY idx_video_pending_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='视频存疑数据';

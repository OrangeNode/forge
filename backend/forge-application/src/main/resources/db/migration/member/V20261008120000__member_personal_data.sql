-- 普通用户个人数据：观看历史（每部影片一行，记录最近观看的线路、集数与进度）与收藏。
-- 影片只保存 ID，展示信息在读取时经影片目录端口取最新值；影片下架后对应记录在列表中被跳过。

CREATE TABLE member_watch_history (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_id BIGINT NOT NULL COMMENT '普通用户主键',
    video_id BIGINT NOT NULL COMMENT '影片主键',
    line_id BIGINT NULL COMMENT '最近观看的播放线路主键',
    episode_index INT NOT NULL COMMENT '最近观看的分集序号，从 0 开始',
    episode_name VARCHAR(64) NOT NULL COMMENT '最近观看的分集名称',
    position_seconds INT NOT NULL COMMENT '播放位置秒数',
    duration_seconds INT NOT NULL COMMENT '分集总时长秒数，未知为 0',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '最近观看时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_watch_history (member_id, video_id),
    KEY idx_member_watch_history_recent (member_id, updated_at),
    CONSTRAINT fk_member_watch_history_user FOREIGN KEY (member_id) REFERENCES member_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通用户观看历史';

CREATE TABLE member_favorite (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_id BIGINT NOT NULL COMMENT '普通用户主键',
    video_id BIGINT NOT NULL COMMENT '影片主键',
    created_at DATETIME(3) NOT NULL COMMENT '收藏时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_favorite (member_id, video_id),
    KEY idx_member_favorite_recent (member_id, id),
    CONSTRAINT fk_member_favorite_user FOREIGN KEY (member_id) REFERENCES member_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通用户收藏';

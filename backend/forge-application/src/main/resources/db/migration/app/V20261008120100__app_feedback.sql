-- App 意见反馈与播放报错：普通用户提交，后台“反馈处理”页查看并标记已处理。
-- 提交人邮箱、影片与线路名称在提交时保存快照，便于影片或账号变化后仍能看懂原始反馈。

CREATE TABLE app_feedback (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_id BIGINT NOT NULL COMMENT '提交人普通用户主键',
    member_email VARCHAR(128) NOT NULL COMMENT '提交人邮箱快照',
    feedback_type VARCHAR(16) NOT NULL COMMENT '类型 PLAYBACK 播放报错 / GENERAL 意见反馈',
    video_id BIGINT NULL COMMENT '影片主键，播放报错必填',
    video_name VARCHAR(255) NULL COMMENT '影片名称快照',
    line_id BIGINT NULL COMMENT '播放线路主键',
    line_name VARCHAR(128) NULL COMMENT '线路名称快照',
    episode_name VARCHAR(64) NULL COMMENT '分集名称',
    content VARCHAR(500) NOT NULL COMMENT '反馈内容',
    device_name VARCHAR(64) NULL COMMENT '提交设备名称，仅展示',
    app_version VARCHAR(32) NULL COMMENT 'App 版本，仅展示',
    status VARCHAR(16) NOT NULL COMMENT '状态 PENDING 待处理 / RESOLVED 已处理',
    handle_remark VARCHAR(255) NULL COMMENT '处理备注',
    handled_by BIGINT NULL COMMENT '处理管理员主键',
    handled_at DATETIME(3) NULL COMMENT '处理时间UTC',
    created_at DATETIME(3) NOT NULL COMMENT '提交时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    KEY idx_app_feedback_status (status, id),
    KEY idx_app_feedback_member (member_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='App 意见反馈与播放报错';

-- “App 管理 → 反馈处理”页面与权限，挂在既有 App 管理目录下。
INSERT INTO sys_menu(parent_id,name,menu_type,icon,route_key,perm_codes,permissions_json,sort_no,deleted,created_at,updated_at)
SELECT id,'反馈处理','page','message','app-feedback','app:feedback:view,app:feedback:handle','[{"code":"app:feedback:view","name":"查看反馈","description":"查看意见反馈与播放报错"},{"code":"app:feedback:handle","name":"处理反馈","description":"标记反馈为已处理并填写备注"}]',6,0,UTC_TIMESTAMP(3),UTC_TIMESTAMP(3)
FROM sys_menu WHERE parent_id=0 AND name='App 管理' AND deleted=0;

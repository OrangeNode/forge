-- 缺失资料策略在管理端持久化；旧设置和旧运行快照保持原有行为。
ALTER TABLE video_settings ADD COLUMN missing_policies TEXT NULL COMMENT '各字段缺失处理策略JSON';

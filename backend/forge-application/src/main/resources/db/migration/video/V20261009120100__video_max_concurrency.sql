-- 采集多并发（2026-10-09 用户要求“采集视频时应该允许多并发”）：不同资源站可以同时采集，
-- 后台可设同时运行的最大采集数；同一资源站仍只有一个活动运行、按页顺序采集，断点续采不变。
ALTER TABLE video_settings ADD COLUMN max_concurrency INT NOT NULL DEFAULT 3 COMMENT '同时运行的最大采集数（不同资源站）';

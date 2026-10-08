-- 片名拼音首字母：电视端只能用遥控器输入字母，按首字母（如 qyn → 庆余年）搜索影片。
-- 历史影片在应用启动后由后台任务分批回填，新采集与编辑时同步写入。
ALTER TABLE video_video
    ADD COLUMN name_initials VARCHAR(255) NULL COMMENT '片名拼音首字母（小写），为空表示尚未回填';

CREATE INDEX idx_video_video_initials ON video_video (name_initials(32));

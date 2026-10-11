-- 开屏增加电视端封面图（2026-10-11 用户要求：电视与手机开屏图尺寸不同，需要单独配置）。
-- 可为空：为空时电视端继续使用手机封面图，已有开屏不受影响。

ALTER TABLE app_splash
    ADD COLUMN tv_image_file_id BIGINT NULL COMMENT '电视端封面图文件主键，空表示使用手机封面图' AFTER image_file_id;

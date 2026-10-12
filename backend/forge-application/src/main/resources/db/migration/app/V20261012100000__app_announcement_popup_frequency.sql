-- 公告弹出频率（2026-10-12 用户要求：每条公告单独设置）。
-- ONCE 每个用户只弹一次（原有行为，已有公告保持不变）/ DAILY 每天弹一次 / EVERY_LAUNCH 每次进入 App 都弹。
-- 频率只在服务端判断，App 无需升级；无论哪种频率，标题、内容或按钮修改后都会再弹。

ALTER TABLE app_announcement
    ADD COLUMN popup_frequency VARCHAR(16) NOT NULL DEFAULT 'ONCE'
        COMMENT '弹出频率 ONCE 每人一次/DAILY 每天一次/EVERY_LAUNCH 每次进入 App' AFTER action_value;

-- 定时任务改为五段 cron 表达式（分 时 日 月 周），调度时区统一取配置 forge.video.default-zone，
-- 不再按任务保存执行频率、间隔、星期、时间与时区。已有任务按原设置换算为等价表达式。
ALTER TABLE video_task
    ADD COLUMN cron_expression VARCHAR(128) NULL COMMENT '五段 cron 表达式 分 时 日 月 周' AFTER upstream_category_id;

UPDATE video_task
SET cron_expression = CASE
    WHEN frequency = 'INTERVAL' AND interval_hours < 24
        THEN CONCAT('0 */', interval_hours, ' * * *')
    WHEN frequency = 'INTERVAL'
        THEN '0 0 * * *'
    WHEN frequency = 'WEEKLY'
        THEN CONCAT(CAST(SUBSTRING(time_of_day, 4, 2) AS UNSIGNED), ' ',
                    CAST(SUBSTRING(time_of_day, 1, 2) AS UNSIGNED), ' * * ', MOD(weekday, 7))
    ELSE CONCAT(CAST(SUBSTRING(time_of_day, 4, 2) AS UNSIGNED), ' ',
                CAST(SUBSTRING(time_of_day, 1, 2) AS UNSIGNED), ' * * *')
END;

ALTER TABLE video_task
    MODIFY COLUMN cron_expression VARCHAR(128) NOT NULL COMMENT '五段 cron 表达式 分 时 日 月 周',
    DROP COLUMN frequency,
    DROP COLUMN interval_hours,
    DROP COLUMN weekday,
    DROP COLUMN time_of_day,
    DROP COLUMN zone_id;

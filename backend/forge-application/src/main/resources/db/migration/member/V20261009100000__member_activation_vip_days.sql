-- 激活码改为带会员时长（2026-10-09 用户要求“一个月、3 个月、半年、一年的激活码，从注册开始计算时间”）：
-- 生成时选天数，用户用该码注册成功时从注册时刻起开通相应天数的 VIP，不再由后台设定固定截止日期。
-- 已有的码没有时长（NULL 表示注册后不开通 VIP）；expire_at 只保留给旧数据判断过期。
ALTER TABLE member_activation_code ADD COLUMN vip_days INT NULL COMMENT '注册成功后开通的 VIP 天数，从注册时刻起算' AFTER status;

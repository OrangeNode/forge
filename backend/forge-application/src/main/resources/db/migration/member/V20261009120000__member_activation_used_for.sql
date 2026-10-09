-- 激活码续费（2026-10-09 用户要求“会员中心新增激活码续费”）：已登录用户也可以用同一批激活码延长 VIP。
-- 一码仍只能使用一次；记录用途，便于后台区分是注册用掉还是续费用掉。此前已使用的码全部来自注册。
ALTER TABLE member_activation_code ADD COLUMN used_for VARCHAR(16) NULL COMMENT '用途 REGISTER 注册/RENEW 续费，未使用为空' AFTER used_ip;
UPDATE member_activation_code SET used_for = 'REGISTER' WHERE status = 'USED';

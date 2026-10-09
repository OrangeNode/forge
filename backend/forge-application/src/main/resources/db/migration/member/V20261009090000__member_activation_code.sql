-- 注册激活码（2026-10-09 用户要求“注册需要激活码”）：与 VIP 无关，只用于开放注册时新建账号。
-- 每个码只能注册一次；状态 UNUSED/USED/REVOKED，是否过期由 expire_at 判断，不另写状态。
-- 激活码只存规范化后的大写字母与数字（不含短横线），由应用层规范化保证同一码只有一种写法。

CREATE TABLE member_activation_code (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    code VARCHAR(32) NOT NULL COMMENT '激活码，大写字母与数字，不含分隔符',
    status VARCHAR(16) NOT NULL COMMENT '状态 UNUSED/USED/REVOKED',
    expire_at DATETIME(3) NULL COMMENT '有效期截止时间UTC，空表示长期有效',
    remark VARCHAR(255) NULL COMMENT '备注，如发给谁、哪一批',
    used_member_id BIGINT NULL COMMENT '使用该码注册的普通用户主键',
    used_username VARCHAR(20) NULL COMMENT '使用该码注册的用户名，便于列表展示与搜索',
    used_at DATETIME(3) NULL COMMENT '使用时间UTC',
    used_ip VARCHAR(45) NULL COMMENT '注册来源地址',
    revoked_at DATETIME(3) NULL COMMENT '作废时间UTC',
    created_by BIGINT NULL COMMENT '生成该码的管理员主键',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_activation_code (code),
    KEY idx_member_activation_status (status, id),
    KEY idx_member_activation_member (used_member_id),
    CONSTRAINT fk_member_activation_user FOREIGN KEY (used_member_id) REFERENCES member_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='注册激活码';

-- 普通用户（App 用户）表：与管理员账号完全分开；账号只做启停，不逻辑删除。
-- 邮箱唯一键使用不区分大小写的 utf8mb4_0900_ai_ci，应用层同时把邮箱规范化为小写。

CREATE TABLE member_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    email VARCHAR(128) NOT NULL COMMENT '登录邮箱，小写规范化',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码编码结果',
    nickname VARCHAR(32) NOT NULL COMMENT '昵称',
    avatar_url VARCHAR(512) NULL COMMENT '头像地址',
    status VARCHAR(16) NOT NULL COMMENT '账号状态 ENABLED/DISABLED',
    vip_expire_at DATETIME(3) NULL COMMENT 'VIP 到期时间UTC，空表示从未开通',
    session_version BIGINT NOT NULL DEFAULT 0 COMMENT '会话版本，递增后此前令牌全部失效',
    register_ip VARCHAR(45) NULL COMMENT '注册来源地址',
    last_login_at DATETIME(3) NULL COMMENT '最近登录时间UTC',
    last_login_ip VARCHAR(45) NULL COMMENT '最近登录来源地址',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_user_email (email),
    KEY idx_member_user_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='普通用户';

CREATE TABLE member_device (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_id BIGINT NOT NULL COMMENT '普通用户主键',
    device_code VARCHAR(64) NOT NULL COMMENT 'App 首次安装生成的设备编号',
    device_name VARCHAR(64) NOT NULL COMMENT '设备名称，仅展示',
    client_ip VARCHAR(45) NULL COMMENT '最近来源地址，仅展示',
    token_digest CHAR(64) NULL COMMENT '当前令牌摘要，离线后清空',
    online TINYINT NOT NULL COMMENT '是否在线',
    offline_reason VARCHAR(16) NULL COMMENT '离线原因 LOGOUT/LIMIT/ADMIN/REVOKED/EXPIRED',
    login_at DATETIME(3) NOT NULL COMMENT '最近登录时间UTC',
    last_active_at DATETIME(3) NOT NULL COMMENT '最近活跃时间UTC',
    offline_at DATETIME(3) NULL COMMENT '离线时间UTC',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_device_code (member_id, device_code),
    KEY idx_member_device_online (member_id, online, login_at),
    CONSTRAINT fk_member_device_user FOREIGN KEY (member_id) REFERENCES member_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='普通用户登录设备';

CREATE TABLE member_vip_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    member_id BIGINT NOT NULL COMMENT '普通用户主键',
    change_type VARCHAR(16) NOT NULL COMMENT '变更类型 EXTEND/SET/CANCEL',
    days INT NULL COMMENT '延长天数，仅 EXTEND',
    before_expire_at DATETIME(3) NULL COMMENT '变更前到期时间UTC',
    after_expire_at DATETIME(3) NULL COMMENT '变更后到期时间UTC',
    remark VARCHAR(255) NULL COMMENT '备注',
    operator_id BIGINT NULL COMMENT '操作管理员主键',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间UTC',
    PRIMARY KEY (id),
    KEY idx_member_vip_log_member (member_id, id),
    CONSTRAINT fk_member_vip_log_user FOREIGN KEY (member_id) REFERENCES member_user (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_as_cs COMMENT='普通用户 VIP 变更记录';

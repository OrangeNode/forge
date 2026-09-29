-- M4 审计模块：登录日志与操作日志，以及审计相关的菜单与权限。
--
-- 约定（见 docs/conventions/数据库设计规范.md）：
--   1. 审计表只追加记录，不接受普通修改；按保留期限清理，期限由 forge.audit.* 配置；
--   2. 保存身份快照（操作者名称），不外键依赖随时可变的账号记录；
--   3. 不记录密码、令牌、请求体与文件内容，只记录动作、对象、结果 code 与 traceId。

CREATE TABLE audit_login_log
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(64) NOT NULL COMMENT '登录尝试使用的用户名（规范化后）',
    result     VARCHAR(16) NOT NULL COMMENT '结果：success 成功，failure 失败',
    reason     VARCHAR(64) NULL COMMENT '失败原因分类：credentials 凭据错误，disabled 账号停用，rate_limited 触发限流',
    client_ip  VARCHAR(45) NULL COMMENT '来源地址（IPv4 或 IPv6）',
    trace_id   VARCHAR(64) NULL COMMENT '请求追踪编号，用于关联服务端日志',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_audit_login_log_username (username, created_at),
    KEY idx_audit_login_log_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='登录日志，只追加';

CREATE TABLE audit_operation_log
(
    id            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_type VARCHAR(16) NOT NULL COMMENT '操作者类型：ADMIN 管理员，SYSTEM 系统任务',
    operator_id   BIGINT      NULL COMMENT '操作者管理员 ID，系统任务为空',
    operator_name VARCHAR(64) NULL COMMENT '操作者名称快照，系统任务为空',
    action        VARCHAR(64) NOT NULL COMMENT '动作代码，例如 system:admin:create',
    resource_type VARCHAR(64) NULL COMMENT '对象类型，例如 admin、storage-config',
    resource_id   VARCHAR(64) NULL COMMENT '对象 ID 快照，对外字符串',
    result_code   INT         NOT NULL COMMENT '业务结果 code，0 表示成功',
    trace_id      VARCHAR(64) NULL COMMENT '请求追踪编号，用于关联服务端日志',
    created_at    DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_audit_operation_log_action (action, created_at),
    KEY idx_audit_operation_log_operator (operator_id, created_at),
    KEY idx_audit_operation_log_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='操作日志，只追加';

-- 审计模块菜单
INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
VALUES (0, '审计中心', NULL, 30, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '操作日志', 'audit-operation', 10, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '审计中心' LIMIT 1) parent;

INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
SELECT parent.id, '登录日志', 'audit-login', 20, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM (SELECT id FROM sys_menu WHERE route_key IS NULL AND name = '审计中心' LIMIT 1) parent;

-- 审计模块权限
INSERT INTO sys_permission (code, name, description, deleted, created_at, updated_at) VALUES
    ('audit:login:view', '查询登录日志', '查询登录成功与失败记录', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    ('audit:operation:view', '查询操作日志', '查询操作审计记录', 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

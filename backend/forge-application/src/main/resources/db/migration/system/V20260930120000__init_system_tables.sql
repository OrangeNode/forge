-- M3 系统表：管理员、角色、菜单、权限及三张关系表。
--
-- 约定（见 docs/conventions/数据库设计规范.md）：
--   1. 主键为有符号 BIGINT AUTO_INCREMENT，业务层 Long，对外字符串；
--   2. 时间列统一 DATETIME(3)，按 UTC 语义读写，因此默认值与写入都使用 UTC；
--   3. 账号只做启停，不开放删除流程，因此 sys_admin 不设 deleted 字段；
--      角色、菜单、权限为主数据，使用 deleted 逻辑删除；关系表物理删除；
--   4. 用户名与角色代码的唯一键显式使用 utf8mb4_0900_ai_ci，
--      即唯一性不区分大小写，避免出现 Admin 与 admin 两个账号。
--
-- 种子数据只包含超级管理员角色与前端已存在的“概览”菜单：
--   权限数据与管理员、角色、菜单、权限的管理接口一起在 M5 新增，
--   不在本轮为尚未实现的接口预建权限行。
--   超级管理员角色由 forge.security.super-role-code 识别，解析时拥有全部权限，
--   因此不需要为它逐条插入 sys_role_permission 记录。

CREATE TABLE sys_admin
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录用户名，不区分大小写唯一',
    password_hash VARCHAR(100) NOT NULL COMMENT '密码安全编码结果，不保存明文',
    display_name  VARCHAR(64)  NOT NULL COMMENT '显示名称',
    status        VARCHAR(16)  NOT NULL COMMENT '账号状态：enabled 启用，disabled 停用',
    last_login_at DATETIME(3)  NULL COMMENT '最近一次登录成功时间（UTC）',
    created_at    DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at    DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    created_by    BIGINT       NULL COMMENT '创建者管理员 ID，初始化流程为 NULL',
    updated_by    BIGINT       NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_admin_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理员账号';

CREATE TABLE sys_role
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(64)  NOT NULL COMMENT '角色代码，不区分大小写唯一，例如 super_admin',
    name        VARCHAR(64)  NOT NULL COMMENT '角色名称',
    description VARCHAR(255) NULL COMMENT '角色说明',
    sort_no     INT          NOT NULL DEFAULT 0 COMMENT '展示顺序，数值小的在前',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    created_by  BIGINT       NULL COMMENT '创建者管理员 ID',
    updated_by  BIGINT       NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_role_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色';

CREATE TABLE sys_menu
(
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    parent_id  BIGINT      NOT NULL DEFAULT 0 COMMENT '父菜单 ID，0 表示顶级',
    name       VARCHAR(64) NOT NULL COMMENT '菜单名称',
    route_key  VARCHAR(64) NULL COMMENT '前端本地路由标识，目录为空，前端只接受白名单内的标识',
    sort_no    INT         NOT NULL DEFAULT 0 COMMENT '同级展示顺序，数值小的在前',
    deleted    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间（UTC）',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间（UTC）',
    created_by BIGINT      NULL COMMENT '创建者管理员 ID',
    updated_by BIGINT      NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_menu_route_key (route_key),
    KEY idx_sys_menu_parent (parent_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='菜单';

CREATE TABLE sys_permission
(
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    code        VARCHAR(128) NOT NULL COMMENT '权限代码，格式为模块:资源:动作',
    name        VARCHAR(64)  NOT NULL COMMENT '权限名称',
    description VARCHAR(255) NULL COMMENT '权限说明',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 正常，1 已删除',
    created_at  DATETIME(3)  NOT NULL COMMENT '创建时间（UTC）',
    updated_at  DATETIME(3)  NOT NULL COMMENT '更新时间（UTC）',
    created_by  BIGINT       NULL COMMENT '创建者管理员 ID',
    updated_by  BIGINT       NULL COMMENT '更新者管理员 ID',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_permission_code (code)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='权限';

CREATE TABLE sys_admin_role
(
    admin_id   BIGINT      NOT NULL COMMENT '管理员 ID',
    role_id    BIGINT      NOT NULL COMMENT '角色 ID',
    created_at DATETIME(3) NOT NULL COMMENT '关联创建时间（UTC）',
    created_by BIGINT      NULL COMMENT '操作者管理员 ID',
    PRIMARY KEY (admin_id, role_id),
    KEY idx_sys_admin_role_role (role_id),
    CONSTRAINT fk_sys_admin_role_admin FOREIGN KEY (admin_id) REFERENCES sys_admin (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_admin_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='管理员与角色关系，物理删除';

CREATE TABLE sys_role_menu
(
    role_id    BIGINT      NOT NULL COMMENT '角色 ID',
    menu_id    BIGINT      NOT NULL COMMENT '菜单 ID',
    created_at DATETIME(3) NOT NULL COMMENT '关联创建时间（UTC）',
    created_by BIGINT      NULL COMMENT '操作者管理员 ID',
    PRIMARY KEY (role_id, menu_id),
    KEY idx_sys_role_menu_menu (menu_id),
    CONSTRAINT fk_sys_role_menu_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_role_menu_menu FOREIGN KEY (menu_id) REFERENCES sys_menu (id) ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色与菜单关系，物理删除';

CREATE TABLE sys_role_permission
(
    role_id       BIGINT      NOT NULL COMMENT '角色 ID',
    permission_id BIGINT      NOT NULL COMMENT '权限 ID',
    created_at    DATETIME(3) NOT NULL COMMENT '关联创建时间（UTC）',
    created_by    BIGINT      NULL COMMENT '操作者管理员 ID',
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission (permission_id),
    CONSTRAINT fk_sys_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sys_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
        ON DELETE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='角色与权限关系，物理删除';

-- 种子：超级管理员角色。初始化管理员由启动引导创建并关联该角色。
INSERT INTO sys_role (code, name, description, sort_no, deleted, created_at, updated_at)
VALUES ('super_admin', '超级管理员', '拥有全部权限的内置角色，由配置识别，不需要逐条授权', 0, 0, UTC_TIMESTAMP(3),
        UTC_TIMESTAMP(3));

-- 种子：前端静态路由白名单中已存在的“概览”页面。
INSERT INTO sys_menu (parent_id, name, route_key, sort_no, deleted, created_at, updated_at)
VALUES (0, '概览', 'home', 0, 0, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

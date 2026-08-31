-- ============================================================
-- System Management (RBAC)
-- ============================================================

CREATE TABLE IF NOT EXISTS t_sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(256) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT '',
    email       VARCHAR(128) DEFAULT '',
    phone       VARCHAR(32)  DEFAULT '',
    dept_id     BIGINT       DEFAULT NULL,
    status      TINYINT      DEFAULT 0,
    remark      VARCHAR(256) DEFAULT '',
    login_time  DATETIME    DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS i_sys_user_username ON t_sys_user (username);

CREATE TABLE IF NOT EXISTS t_sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    role_name   VARCHAR(64)  NOT NULL,
    role_key    VARCHAR(64)  NOT NULL,
    status      TINYINT      DEFAULT 0,
    remark      VARCHAR(256) DEFAULT '',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS i_sys_role_key ON t_sys_role (role_key);

CREATE TABLE IF NOT EXISTS t_sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    menu_name   VARCHAR(64)  NOT NULL,
    parent_id   BIGINT       DEFAULT 0,
    path        VARCHAR(256) DEFAULT '',
    component   VARCHAR(256) DEFAULT '',
    icon        VARCHAR(64)  DEFAULT '',
    perms       VARCHAR(128) DEFAULT '',
    menu_type   CHAR(1)      NOT NULL DEFAULT 'C',
    order_num   INT          DEFAULT 0,
    status      TINYINT      DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS i_sys_menu_parent ON t_sys_menu (parent_id);

CREATE TABLE IF NOT EXISTS t_sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    dept_name   VARCHAR(64)  NOT NULL,
    parent_id   BIGINT       DEFAULT 0,
    ancestors   VARCHAR(512) DEFAULT '',
    order_num   INT          DEFAULT 0,
    leader      VARCHAR(64)  DEFAULT '',
    phone       VARCHAR(32)  DEFAULT '',
    status      TINYINT      DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_sys_notice (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    title        VARCHAR(128)  NOT NULL,
    content      TEXT          DEFAULT '',
    type         TINYINT       DEFAULT 1,
    status       TINYINT       DEFAULT 0,
    publish_time DATETIME     DEFAULT NULL,
    create_by    VARCHAR(64)   DEFAULT '',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_sys_oper_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        DEFAULT NULL,
    username   VARCHAR(64)   DEFAULT '',
    operation  VARCHAR(128)  DEFAULT '',
    method     VARCHAR(256)  DEFAULT '',
    params     TEXT          DEFAULT '',
    result     TEXT          DEFAULT '',
    error_msg  TEXT          DEFAULT '',
    ip         VARCHAR(64)   DEFAULT '',
    cost_time  BIGINT        DEFAULT 0,
    oper_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE INDEX IF NOT EXISTS i_sys_oper_log_user ON t_sys_oper_log (user_id);
CREATE INDEX IF NOT EXISTS i_sys_oper_log_time ON t_sys_oper_log (oper_time);

CREATE TABLE IF NOT EXISTS t_sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS i_sys_user_role ON t_sys_user_role (user_id, role_id);

CREATE TABLE IF NOT EXISTS t_sys_role_menu (
    id      BIGINT NOT NULL AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    menu_id BIGINT NOT NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS i_sys_role_menu ON t_sys_role_menu (role_id, menu_id);

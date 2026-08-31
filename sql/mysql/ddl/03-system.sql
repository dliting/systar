-- ============================================================

CREATE TABLE IF NOT EXISTS t_sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(64)  NOT NULL COMMENT '用户名',
    password    VARCHAR(256) NOT NULL COMMENT 'BCrypt密码',
    nickname    VARCHAR(64)  DEFAULT '' COMMENT '昵称',
    email       VARCHAR(128) DEFAULT '' COMMENT '邮箱',
    phone       VARCHAR(32)  DEFAULT '' COMMENT '手机号',
    dept_id     BIGINT       DEFAULT NULL COMMENT '部门ID',
    status      TINYINT      DEFAULT 0 COMMENT '状态: 0-正常, 1-停用',
    remark      VARCHAR(256) DEFAULT '' COMMENT '备注',
    login_time  DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX i_sys_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统用户';

CREATE TABLE IF NOT EXISTS t_sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_name   VARCHAR(64)  NOT NULL COMMENT '角色名称',
    role_key    VARCHAR(64)  NOT NULL COMMENT '角色标识',
    status      TINYINT      DEFAULT 0 COMMENT '状态: 0-正常, 1-停用',
    remark      VARCHAR(256) DEFAULT '' COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE INDEX i_sys_role_key (role_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统角色';

CREATE TABLE IF NOT EXISTS t_sys_menu (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
    menu_name   VARCHAR(64)  NOT NULL COMMENT '菜单名称',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父菜单ID',
    path        VARCHAR(256) DEFAULT '' COMMENT '路由路径',
    component   VARCHAR(256) DEFAULT '' COMMENT '组件路径',
    icon        VARCHAR(64)  DEFAULT '' COMMENT '图标',
    perms       VARCHAR(128) DEFAULT '' COMMENT '权限标识',
    menu_type   CHAR(1)      NOT NULL DEFAULT 'C' COMMENT '类型: M-目录, C-菜单, F-按钮权限',
    order_num   INT          DEFAULT 0 COMMENT '排序',
    status      TINYINT      DEFAULT 0 COMMENT '状态: 0-正常, 1-停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    INDEX i_sys_menu_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统菜单';

CREATE TABLE IF NOT EXISTS t_sys_dept (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    dept_name   VARCHAR(64)  NOT NULL COMMENT '部门名称',
    parent_id   BIGINT       DEFAULT 0 COMMENT '父部门ID',
    ancestors   VARCHAR(512) DEFAULT '' COMMENT '祖级列表',
    order_num   INT          DEFAULT 0 COMMENT '排序',
    leader      VARCHAR(64)  DEFAULT '' COMMENT '负责人',
    phone       VARCHAR(32)  DEFAULT '' COMMENT '联系电话',
    status      TINYINT      DEFAULT 0 COMMENT '状态: 0-正常, 1-停用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='部门管理';

CREATE TABLE IF NOT EXISTS t_sys_notice (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '通知ID',
    title        VARCHAR(128)  NOT NULL COMMENT '标题',
    content      TEXT          COMMENT '内容',
    type         TINYINT       DEFAULT 1 COMMENT '类型: 1-通知, 2-公告',
    status       TINYINT       DEFAULT 0 COMMENT '状态: 0-草稿, 1-已发布',
    publish_time DATETIME      DEFAULT NULL COMMENT '发布时间',
    create_by    VARCHAR(64)   DEFAULT '' COMMENT '创建人',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='通知公告';

CREATE TABLE IF NOT EXISTS t_sys_oper_log (
    id         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id    BIGINT        DEFAULT NULL COMMENT '用户ID',
    username   VARCHAR(64)   DEFAULT '' COMMENT '用户名',
    operation  VARCHAR(128)  DEFAULT '' COMMENT '操作描述',
    method     VARCHAR(256)  DEFAULT '' COMMENT '请求方法',
    params     TEXT          COMMENT '请求参数',
    result     TEXT          COMMENT '返回结果',
    error_msg  TEXT          COMMENT '错误信息',
    ip         VARCHAR(64)   DEFAULT '' COMMENT 'IP地址',
    cost_time  BIGINT        DEFAULT 0 COMMENT '耗时(ms)',
    oper_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    INDEX i_sys_oper_log_user (user_id),
    INDEX i_sys_oper_log_time (oper_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='操作日志';

CREATE TABLE IF NOT EXISTS t_sys_user_role (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (id),
    UNIQUE INDEX i_sys_user_role (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='用户角色关联';

CREATE TABLE IF NOT EXISTS t_sys_role_menu (
    id      BIGINT NOT NULL AUTO_INCREMENT COMMENT 'ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (id),
    UNIQUE INDEX i_sys_role_menu (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='角色菜单关联';

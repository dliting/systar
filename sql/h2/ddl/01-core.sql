-- ============================================================================
-- 数据库: db_systar
-- 产品: systar
-- 数据库: H2 (MySQL compatibility mode)
-- 说明:
--   本文件用于创建 systar 监控系统用到的全部数据表。
--   所有表使用 CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- ============================================================================

-- ============================================================================
-- 站点/空间
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_space (
    id              INT             NOT NULL                       ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    parent          INT             NOT NULL                       ,
    area            INT             NULL                           ,
    sequence         INT             NOT NULL DEFAULT 0             ,
    show_in_client  TINYINT         NOT NULL DEFAULT 1             ,
    type_name       VARCHAR(100)    NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_space_name ON t_space (name);

-- ============================================================================
-- 监控服务
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_service (
    id              INT             NOT NULL                       ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    parent          INT             NOT NULL                       ,
    mode            TINYINT         NULL                           ,
    driver_class    VARCHAR(255)    NULL                           ,
    max_connections INT             NULL                           ,
    type_name       VARCHAR(100)    NULL                           ,
    PRIMARY KEY (id)
);

-- ============================================================================
-- 设备
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_device (
    id                    INT             NOT NULL                       ,
    name                  VARCHAR(63)     NOT NULL                       ,
    caption               VARCHAR(255)    NULL                           ,
    parent                INT             NOT NULL                       ,
    catalog               SMALLINT        NULL                           ,
    vendor                VARCHAR(255)    NULL                           ,
    purchase_date         DATETIME        NULL                           ,
    warranty_date         DATE            NULL                           ,
    health_index          FLOAT           NULL                           ,
    model                 VARCHAR(100)    NULL                           ,
    serial_number         VARCHAR(100)    NULL                           ,
    install_date          DATE            NULL                           ,
    lifecycle_status      VARCHAR(20)     DEFAULT 'IN_SERVICE'           ,
    responsible_person    VARCHAR(100)    NULL                           ,
    department            VARCHAR(100)    NULL                           ,
    supplier_contact      VARCHAR(200)    NULL                           ,
    maintenance_cycle     INT             NULL                           ,
    last_maintenance_date DATE            NULL                           ,
    remark                TEXT            NULL                           ,
    type_name             VARCHAR(100)    NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_device_name ON t_device (name);
CREATE INDEX IF NOT EXISTS i_device_lifecycle ON t_device (lifecycle_status);
CREATE INDEX IF NOT EXISTS i_device_serial ON t_device (serial_number);

-- ============================================================================
-- 监测器
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_probe (
    id              INT             NOT NULL                       ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    parent          INT             NOT NULL                       ,
    source          INT             NULL                           ,
    unit            VARCHAR(31)     NULL                           ,
    time_interval   VARCHAR(31)     NULL                           ,
    saving_interval VARCHAR(31)     NULL                           ,
    warn_cond       VARCHAR(255)    NULL                           ,
    transform       VARCHAR(255)    NULL                           ,
    catalog         SMALLINT        NULL                           ,
    monitor_kind    TINYINT         NULL                           ,
    min_value       FLOAT           NULL                           ,
    max_value       FLOAT           NULL                           ,
    type_name       VARCHAR(100)    NULL                           ,
    is_virtual      TINYINT         NULL DEFAULT 0                 ,
    expression      VARCHAR(500)    NULL                           ,
    depends_on      VARCHAR(255)    NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_probe_type ON t_probe (catalog);

-- ============================================================================
-- 控制器
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_control (
    id              INT             NOT NULL                       ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    parent          INT             NOT NULL                       ,
    source          INT             NULL                           ,
    unit            VARCHAR(31)     NULL                           ,
    time_interval   VARCHAR(31)     NULL                           ,
    saving_interval VARCHAR(31)     NULL                           ,
    catalog         SMALLINT        NULL                           ,
    transform       VARCHAR(255)    NULL                           ,
    warn_cond       VARCHAR(255)    NULL                           ,
    refresh_delay   INT             NULL DEFAULT 1000              ,
    min_value       FLOAT           NULL                           ,
    max_value       FLOAT           NULL                           ,
    type_name       VARCHAR(100)    NULL                           ,
    PRIMARY KEY (id)
);

-- ============================================================================
-- 统一资产视图
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_asset (
    id              BIGINT          NOT NULL AUTO_INCREMENT        ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    kind            TINYINT         NOT NULL                       ,
    type_id         BIGINT          NULL                           ,
    parent_id       BIGINT          NULL                           ,
    state           TINYINT         NULL                           ,
    enabled         INT             NULL DEFAULT 1                 ,
    sort            INT             NULL DEFAULT 0                 ,
    space_id        BIGINT          NULL                           ,
    device_id       BIGINT          NULL                           ,
    service_id      BIGINT          NULL                           ,
    probe_id        BIGINT          NULL                           ,
    control_id      BIGINT          NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_asset_kind     ON t_asset (kind);
CREATE INDEX IF NOT EXISTS i_asset_parent   ON t_asset (parent_id);
CREATE INDEX IF NOT EXISTS i_asset_state    ON t_asset (state);

-- ============================================================================
-- 采样数据表 (按类型分表)
-- 注意: 生产环境建议对采样数据表启用按月分区以优化查询和归档性能。
--   分区示例 (以 t_sample_float 为例):
--     PARTITION BY RANGE (TO_DAYS(moment)) (
--       PARTITION p202601 VALUES LESS THAN (TO_DAYS('2026-02-01')),
--       PARTITION p202602 VALUES LESS THAN (TO_DAYS('2026-03-01')),
--       ...
--     );
-- ============================================================================

-- 浮点数采样表
CREATE TABLE IF NOT EXISTS t_sample_float (
    id              BIGINT          NOT NULL AUTO_INCREMENT        ,
    monitor         INT             NOT NULL                       ,
    "value"           FLOAT           NULL                           ,
    moment          DATETIME        NOT NULL                       ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_float_monitor ON t_sample_float (monitor);
CREATE INDEX IF NOT EXISTS i_float_moment  ON t_sample_float (moment);
CREATE INDEX IF NOT EXISTS i_float_monitor_moment ON t_sample_float (monitor, moment);

-- 整数采样表
CREATE TABLE IF NOT EXISTS t_sample_int (
    id              BIGINT          NOT NULL AUTO_INCREMENT        ,
    monitor         INT             NOT NULL                       ,
    "value"           INT             NULL                           ,
    moment          DATETIME        NOT NULL                       ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_int_monitor ON t_sample_int (monitor);
CREATE INDEX IF NOT EXISTS i_int_moment  ON t_sample_int (moment);
CREATE INDEX IF NOT EXISTS i_int_monitor_moment ON t_sample_int (monitor, moment);

-- 布尔值采样表
CREATE TABLE IF NOT EXISTS t_sample_boolean (
    id              BIGINT          NOT NULL AUTO_INCREMENT        ,
    monitor         INT             NOT NULL                       ,
    "value"           TINYINT         NULL                           ,
    moment          DATETIME        NOT NULL                       ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_bool_monitor ON t_sample_boolean (monitor);
CREATE INDEX IF NOT EXISTS i_bool_moment  ON t_sample_boolean (moment);
CREATE INDEX IF NOT EXISTS i_bool_monitor_moment ON t_sample_boolean (monitor, moment);

-- 异常采样表
CREATE TABLE IF NOT EXISTS t_sample_exception (
    id              BIGINT          NOT NULL AUTO_INCREMENT        ,
    monitor         INT             NOT NULL                       ,
    "value"           VARCHAR(255)    NULL                           ,
    moment          DATETIME        NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_exception_monitor ON t_sample_exception (monitor);
CREATE INDEX IF NOT EXISTS i_exception_moment  ON t_sample_exception (moment);

-- ============================================================================
-- 告警
-- ============================================================================

-- 报警规则表
CREATE TABLE IF NOT EXISTS t_alarm_rule (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    asset_id        INT             NOT NULL                       ,
    rule            TINYINT         NOT NULL                       ,
    way             INT             NOT NULL                       ,
    warn_id         INT             NULL                           ,
    message_template VARCHAR(500)   NULL                           ,
    enabled         INT             NULL DEFAULT 1                 ,
    start           INT             NULL                           ,
    dedup_window_seconds INT        NULL DEFAULT 0                 ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_alarm_rule_asset ON t_alarm_rule (asset_id);

-- 报警消息表
CREATE TABLE IF NOT EXISTS t_alarm_message (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    log_id          INT             NOT NULL                       ,
    caption         VARCHAR(100)    NULL                           ,
    state           INT             NULL DEFAULT 1                 ,
    auto            INT             NULL DEFAULT 1                 ,
    alarm_time      DATETIME        NULL                           ,
    recovered       INT             NULL DEFAULT 0                 ,
    warn_id         INT             NULL                           ,
    correlation_group VARCHAR(50)   NULL                           ,
    root_cause_id   INT             NULL                           ,
    suppressed      INT             NULL DEFAULT 0                 ,
    silenced        INT             NULL DEFAULT 0                 ,
    escalation_level TINYINT        NULL DEFAULT 0                 ,
    device_id       INT             NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_alarm_msg_log ON t_alarm_message (log_id);
CREATE INDEX IF NOT EXISTS i_alarm_msg_warn ON t_alarm_message (warn_id);
CREATE INDEX IF NOT EXISTS i_alarm_msg_time ON t_alarm_message (alarm_time);
CREATE INDEX IF NOT EXISTS i_alarm_msg_corr ON t_alarm_message (correlation_group);
CREATE INDEX IF NOT EXISTS i_alarm_msg_device ON t_alarm_message (device_id);

-- 报警日志表
CREATE TABLE IF NOT EXISTS t_error_message_log (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    alarm_rule_id   INT             NULL                           ,
    asset_id        INT             NULL                           ,
    monitor_name    VARCHAR(255)    NULL                           ,
    error_message   VARCHAR(1000)   NULL                           ,
    "value"           VARCHAR(20)     NULL                           ,
    state           INT             NOT NULL                       ,
    warn_id         INT             NULL                           ,
    time            DATETIME        NOT NULL                       ,
    end_time        DATETIME        NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_error_log_rule  ON t_error_message_log (alarm_rule_id);
CREATE INDEX IF NOT EXISTS i_error_log_asset ON t_error_message_log (asset_id);
CREATE INDEX IF NOT EXISTS i_error_log_time  ON t_error_message_log (time);

-- ============================================================================
-- 告警关联与疲劳治理
-- ============================================================================

-- 告警关联规则表
CREATE TABLE IF NOT EXISTS t_alarm_correlation_rule (
    id              INT             NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    device_id       INT             NULL,
    window_seconds  INT             NOT NULL DEFAULT 300,
    enabled         INT             NULL DEFAULT 1,
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 告警升级策略表
CREATE TABLE IF NOT EXISTS t_alarm_escalation_policy (
    id              INT             NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    from_level      TINYINT         NOT NULL,
    to_level        TINYINT         NOT NULL,
    timeout_minutes INT             NOT NULL,
    notify_type     VARCHAR(50)     NULL,
    enabled         INT             NULL DEFAULT 1,
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- 告警静默窗口表
CREATE TABLE IF NOT EXISTS t_alarm_silence_window (
    id              INT             NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100)    NOT NULL,
    device_id       INT             NULL,
    monitor_id      INT             NULL,
    start_time      DATETIME        NOT NULL,
    end_time        DATETIME        NOT NULL,
    reason          VARCHAR(500)    NULL,
    enabled         INT             NULL DEFAULT 1,
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

-- ============================================================================
-- 联动
-- ============================================================================

-- 联动规则主表
CREATE TABLE IF NOT EXISTS t_linkage_rule (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    name            VARCHAR(63)     NOT NULL                       ,
    cause_type      VARCHAR(20)     NOT NULL                       ,
    enabled         TINYINT         NOT NULL DEFAULT 1             ,
    caption         VARCHAR(255)    NULL                           ,
    PRIMARY KEY (id)
);

-- 联动触发条件表
CREATE TABLE IF NOT EXISTS t_linkage_rule_cause (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    rule_id         INT             NOT NULL                       ,
    asset_id        INT             NOT NULL                       ,
    trigger_value   VARCHAR(63)     NOT NULL                       ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_linkage_cause_rule ON t_linkage_rule_cause (rule_id);
CREATE INDEX IF NOT EXISTS i_linkage_cause_asset ON t_linkage_rule_cause (asset_id);

-- 联动执行动作表
CREATE TABLE IF NOT EXISTS t_linkage_rule_effect (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    rule_id         INT             NOT NULL                       ,
    asset_id        INT             NOT NULL                       ,
    command         VARCHAR(255)    NOT NULL                       ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_linkage_effect_rule ON t_linkage_rule_effect (rule_id);
CREATE INDEX IF NOT EXISTS i_linkage_effect_asset ON t_linkage_rule_effect (asset_id);

-- 联动日志表
CREATE TABLE IF NOT EXISTS t_linkage_log (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    rule_id         INT             NOT NULL                       ,
    cause_monitor_id  INT           NULL                           ,
    effect_monitor_id INT           NULL                           ,
    time            DATETIME        NOT NULL                       ,
    effect_command  VARCHAR(255)    NULL                           ,
    success         TINYINT         NULL DEFAULT 1                 ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_linkage_log_rule  ON t_linkage_log (rule_id);
CREATE INDEX IF NOT EXISTS i_linkage_log_time  ON t_linkage_log (time);

-- ============================================================================
-- 定时控制
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_scheduled_task (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    name            VARCHAR(63)     NOT NULL                       ,
    control_id      INT             NOT NULL                       ,
    command         VARCHAR(255)    NOT NULL                       ,
    cron_expression VARCHAR(45)     NOT NULL                       ,
    enabled         TINYINT         NOT NULL DEFAULT 1             ,
    description     VARCHAR(255)    NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_scheduled_task_control ON t_scheduled_task (control_id);

CREATE TABLE IF NOT EXISTS t_scheduled_task_log (
    id            BIGINT          NOT NULL AUTO_INCREMENT          ,
    task_id       INT             NOT NULL                         ,
    task_name     VARCHAR(63)     NOT NULL                         ,
    control_id    INT             NOT NULL                         ,
    command       VARCHAR(255)    NOT NULL                         ,
    execute_time  BIGINT          NOT NULL                         ,
    success       TINYINT         NOT NULL                         ,
    error_message VARCHAR(500)    NULL                             ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_task_log_task ON t_scheduled_task_log (task_id);
CREATE INDEX IF NOT EXISTS i_task_log_time ON t_scheduled_task_log (execute_time);

-- ============================================================================
-- 系统配置
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_system_setting (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    config_key      VARCHAR(127)    NOT NULL                       ,
    "value"         VARCHAR(500)    NULL                           ,
    description     VARCHAR(255)    NULL                           ,
    PRIMARY KEY (id),
    UNIQUE INDEX uk_setting_key (config_key)
);

-- ============================================================================
-- 代码字典
-- ============================================================================

-- 代码分类目录表
CREATE TABLE IF NOT EXISTS t_code_catalog (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    name            VARCHAR(63)     NOT NULL                       ,
    PRIMARY KEY (id)
);

-- 代码字典表
CREATE TABLE IF NOT EXISTS t_code_dict (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    catalog_id      INT             NOT NULL                       ,
    name            VARCHAR(63)     NOT NULL                       ,
    caption         VARCHAR(255)    NULL                           ,
    parent          INT             NULL                           ,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_code_dict_catalog ON t_code_dict (catalog_id);

-- ============================================================================
-- 事件级别
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_event_rank (
    id              INT             NOT NULL AUTO_INCREMENT        ,
    name            VARCHAR(255)    NULL                           ,
    PRIMARY KEY (id)
);


-- ============================================================
-- Asset type configuration
-- ============================================================

CREATE TABLE IF NOT EXISTS t_asset_type_config (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    kind            VARCHAR(20)     NOT NULL,
    type_name       VARCHAR(100)    NOT NULL,
    caption         VARCHAR(255)    NULL,
    driver_class    VARCHAR(255)    NULL,
    properties      TEXT            NULL,
    version         INT             DEFAULT 1,
    content         TEXT            NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS t_asset_attribute (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id        INT             NOT NULL,
    attr_key        VARCHAR(100)    NOT NULL,
    attr_value      TEXT            NULL,
    attr_type       VARCHAR(50)     NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_attr_asset ON t_asset_attribute (asset_id);
CREATE INDEX IF NOT EXISTS i_attr_key ON t_asset_attribute (attr_key);
CREATE UNIQUE INDEX IF NOT EXISTS i_attr_asset_key ON t_asset_attribute (asset_id, attr_key);

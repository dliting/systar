-- ============================================================================
-- 数据库: db_systar
-- 产品: systar
-- 数据库: MySQL 8.x
-- 说明:
--   本文件用于创建 systar 监控系统用到的全部数据表。
--   所有表使用 CREATE TABLE IF NOT EXISTS，可安全重复执行。
-- ============================================================================

-- ============================================================================
-- 站点/空间
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_space (
    id              INT             NOT NULL                        COMMENT '空间id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '名称(工程技术用)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent          INT             NOT NULL                        COMMENT '父资产id',
    area            INT             NULL                            COMMENT '空间面积(平方米)',
    sequence         INT             NOT NULL DEFAULT 0              COMMENT '排序序号',
    show_in_client  TINYINT         NOT NULL DEFAULT 1              COMMENT '是否返回给客户端 1=显示 2=不显示',
    type_name       VARCHAR(100)    NULL                            COMMENT '资产类型名称(关联t_asset_type_config.type_name)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='空间区域表';

CREATE INDEX i_space_name ON t_space (name);

-- ============================================================================
-- 监控服务
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_service (
    id              INT             NOT NULL                        COMMENT '服务id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '服务名称(工程技术用)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent          INT             NOT NULL                        COMMENT '父资产id',
    mode            TINYINT         NULL                            COMMENT '监控模式 0=ACTIVE 1=PASSIVE',
    driver_class    VARCHAR(255)    NULL                            COMMENT '驱动类全限定名',
    max_connections INT             NULL                            COMMENT '最大连接池大小',
    type_name       VARCHAR(100)    NULL                            COMMENT '资产类型名称(关联t_asset_type_config.type_name)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监控服务表';

-- ============================================================================
-- 设备
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_device (
    id                    INT             NOT NULL                        COMMENT '设备id',
    name                  VARCHAR(63)     NOT NULL                        COMMENT '设备名称(工程技术用)',
    caption               VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent                INT             NOT NULL                        COMMENT '父资产id',
    catalog               SMALLINT        NULL                            COMMENT '分类代码',
    vendor                VARCHAR(255)    NULL                            COMMENT '生产厂家',
    purchase_date         DATETIME        NULL                            COMMENT '采购日期',
    warranty_date         DATE            NULL                            COMMENT '保修截止日期',
    health_index          FLOAT           NULL                            COMMENT '健康指数',
    model                 VARCHAR(100)    NULL                            COMMENT '设备型号',
    serial_number         VARCHAR(100)    NULL                            COMMENT '出厂编号/序列号',
    install_date          DATE            NULL                            COMMENT '安装日期',
    lifecycle_status      VARCHAR(20)     DEFAULT 'IN_SERVICE'            COMMENT 'IN_SERVICE/IN_STORAGE/UNDER_REPAIR/RETIRED',
    responsible_person    VARCHAR(100)    NULL                            COMMENT '责任人',
    department            VARCHAR(100)    NULL                            COMMENT '所属部门',
    supplier_contact      VARCHAR(200)    NULL                            COMMENT '供应商联系方式',
    maintenance_cycle     INT             NULL                            COMMENT '维护周期(天)',
    last_maintenance_date DATE            NULL                            COMMENT '上次维护日期',
    remark                TEXT            NULL                            COMMENT '备注',
    type_name             VARCHAR(100)    NULL                            COMMENT '资产类型名称(关联t_asset_type_config.type_name)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备表';

CREATE INDEX i_device_name ON t_device (name);
CREATE INDEX i_device_lifecycle ON t_device (lifecycle_status);
CREATE INDEX i_device_serial ON t_device (serial_number);

-- ============================================================================
-- 监测器
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_probe (
    id              INT             NOT NULL                        COMMENT '监测器id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '名称(工程技术用)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent          INT             NOT NULL                        COMMENT '父资产id(通常为设备)',
    source          INT             NULL                            COMMENT '监测服务id(关联t_service.id)',
    unit            VARCHAR(31)     NULL                            COMMENT '工程单位(如 C, %RH, kWh)',
    time_interval   VARCHAR(31)     NULL                            COMMENT '监测间隔(TimeSpan或Cron格式)',
    saving_interval VARCHAR(31)     NULL                            COMMENT '保存间隔(TimeSpan或Cron格式)',
    warn_cond       VARCHAR(255)    NULL                            COMMENT '警告条件表达式',
    transform       VARCHAR(255)    NULL                            COMMENT '结果转换表达式',
    catalog         SMALLINT        NULL                            COMMENT '分类代码',
    monitor_kind    TINYINT         NULL                            COMMENT '数据类型 0=INT 1=FLOAT 2=BOOLEAN 3=STRING 4=TIMESPAN',
    min_value       FLOAT           NULL                            COMMENT '期望最小值',
    max_value       FLOAT           NULL                            COMMENT '期望最大值',
    type_name       VARCHAR(100)    NULL                            COMMENT '资产类型名称(关联t_asset_type_config.type_name)',
    is_virtual      TINYINT         NULL DEFAULT 0                  COMMENT '是否虚拟探针 0=否 1=是',
    expression      VARCHAR(500)   NULL                            COMMENT '派生计算表达式(如 #probe[101].value / #probe[102].value * 100)',
    depends_on      VARCHAR(255)   NULL                            COMMENT '依赖的探针ID列表(逗号分隔)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监测器表';

CREATE INDEX i_probe_type ON t_probe (catalog);

-- ============================================================================
-- 控制器
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_control (
    id              INT             NOT NULL                        COMMENT '控制器id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '名称(工程技术用)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent          INT             NOT NULL                        COMMENT '父资产id(通常为设备)',
    source          INT             NULL                            COMMENT '监测服务id(关联t_service.id)',
    unit            VARCHAR(31)     NULL                            COMMENT '工程单位',
    time_interval   VARCHAR(31)     NULL                            COMMENT '监测间隔(TimeSpan或Cron格式)',
    saving_interval VARCHAR(31)     NULL                            COMMENT '保存间隔(TimeSpan或Cron格式)',
    catalog         SMALLINT        NULL                            COMMENT '分类代码',
    transform       VARCHAR(255)    NULL                            COMMENT '结果转换表达式',
    warn_cond       VARCHAR(255)    NULL                            COMMENT '警告条件表达式',
    refresh_delay   INT             NULL DEFAULT 1000               COMMENT '刷新延迟(毫秒), 发出控制指令后延迟刷新监测结果',
    min_value       FLOAT           NULL                            COMMENT '期望最小值',
    max_value       FLOAT           NULL                            COMMENT '期望最大值',
    type_name       VARCHAR(100)    NULL                            COMMENT '资产类型名称(关联t_asset_type_config.type_name)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='控制器表';

-- ============================================================================
-- 统一资产视图
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_asset (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '资产id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '名称(工程技术用)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    kind            TINYINT         NOT NULL                        COMMENT '资产类型 0=SPACE 1=DEVICE 2=SERVICE 3=PROBE 4=CONTROL',
    type_id         BIGINT          NULL                            COMMENT '资产类型定义id',
    parent_id       BIGINT          NULL                            COMMENT '父资产行id(t_asset.id,0为根节点)',
    state           TINYINT         NULL                            COMMENT '资产状态 0=NORMAL 1=WARNING 2=ERROR 3=OFFLINE',
    enabled         INT             NULL DEFAULT 1                  COMMENT '是否启用 1=启用 0=禁用',
    sort            INT             NULL DEFAULT 0                  COMMENT '同级排序',
    space_id        BIGINT          NULL                            COMMENT '关联t_space.id(仅kind=SPACE时有效)',
    device_id       BIGINT          NULL                            COMMENT '关联t_device.id(仅kind=DEVICE时有效)',
    service_id      BIGINT          NULL                            COMMENT '关联t_service.id(仅kind=SERVICE时有效)',
    probe_id        BIGINT          NULL                            COMMENT '关联t_probe.id(仅kind=PROBE时有效)',
    control_id      BIGINT          NULL                            COMMENT '关联t_control.id(仅kind=CONTROL时有效)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='统一资产视图表';

CREATE INDEX i_asset_kind     ON t_asset (kind);
CREATE INDEX i_asset_parent   ON t_asset (parent_id);
CREATE INDEX i_asset_state    ON t_asset (state);

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
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    monitor         INT             NOT NULL                        COMMENT '监测器id(关联t_probe.id或t_control.id)',
    value           FLOAT           NULL                            COMMENT '浮点监测值',
    moment          DATETIME        NOT NULL                        COMMENT '采样时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='浮点数采样表';

CREATE INDEX i_float_monitor ON t_sample_float (monitor);
CREATE INDEX i_float_moment  ON t_sample_float (moment);
CREATE INDEX i_float_monitor_moment ON t_sample_float (monitor, moment);

-- 整数采样表
CREATE TABLE IF NOT EXISTS t_sample_int (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    monitor         INT             NOT NULL                        COMMENT '监测器id(关联t_probe.id或t_control.id)',
    value           INT             NULL                            COMMENT '整数监测值',
    moment          DATETIME        NOT NULL                        COMMENT '采样时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='整数采样表';

CREATE INDEX i_int_monitor ON t_sample_int (monitor);
CREATE INDEX i_int_moment  ON t_sample_int (moment);
CREATE INDEX i_int_monitor_moment ON t_sample_int (monitor, moment);

-- 布尔值采样表
CREATE TABLE IF NOT EXISTS t_sample_boolean (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    monitor         INT             NOT NULL                        COMMENT '监测器id(关联t_probe.id或t_control.id)',
    value           TINYINT         NULL                            COMMENT '布尔监测值 0=false 1=true',
    moment          DATETIME        NOT NULL                        COMMENT '采样时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='布尔值采样表';

CREATE INDEX i_bool_monitor ON t_sample_boolean (monitor);
CREATE INDEX i_bool_moment  ON t_sample_boolean (moment);
CREATE INDEX i_bool_monitor_moment ON t_sample_boolean (monitor, moment);

-- 异常采样表
CREATE TABLE IF NOT EXISTS t_sample_exception (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    monitor         INT             NOT NULL                        COMMENT '监测器id(关联t_probe.id或t_control.id)',
    value           VARCHAR(255)    NULL                            COMMENT '异常描述',
    moment          DATETIME        NULL                            COMMENT '发现异常时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='监测异常采样表';

CREATE INDEX i_exception_monitor ON t_sample_exception (monitor);
CREATE INDEX i_exception_moment  ON t_sample_exception (moment);

-- ============================================================================
-- 告警
-- ============================================================================

-- 报警规则表
CREATE TABLE IF NOT EXISTS t_alarm_rule (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    asset_id        INT             NOT NULL                        COMMENT '对应资产id',
    rule            TINYINT         NOT NULL                        COMMENT '报警策略 0=ONLY_ONCE 1=CONTINUOUS 2=SELECTIVE',
    way             INT             NOT NULL                        COMMENT '报警方式(位掩码) 1=声音 2=邮件 4=UI',
    warn_id         INT             NULL                            COMMENT '事件级别id',
    message_template VARCHAR(500)   NULL                            COMMENT '报警消息模板',
    enabled         INT             NULL DEFAULT 1                  COMMENT '是否启用 1=启用 0=禁用',
    start           INT             NULL                            COMMENT '从第N次连续错误开始报警',
    dedup_window_seconds INT        NULL DEFAULT 0                  COMMENT '去重时间窗口(秒,0=不限制)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报警规则表';

CREATE INDEX i_alarm_rule_asset ON t_alarm_rule (asset_id);

-- 报警消息表
CREATE TABLE IF NOT EXISTS t_alarm_message (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    log_id          INT             NOT NULL                        COMMENT '关联t_error_message_log.id',
    caption         VARCHAR(100)    NULL                            COMMENT '报警标题',
    state           INT             NULL DEFAULT 1                  COMMENT '处理状态 1=未处理 2=已处理',
    auto            INT             NULL DEFAULT 1                  COMMENT '报警类型 1=自动 2=手动',
    alarm_time      DATETIME        NULL                            COMMENT '报警时间',
    recovered       INT             NULL DEFAULT 0                  COMMENT '是否已恢复 0=未恢复 1=已恢复',
    warn_id         INT             NULL                            COMMENT '告警等级(t_event_rank.id)',
    correlation_group VARCHAR(50)   NULL                            COMMENT '关联组ID',
    root_cause_id   INT             NULL                            COMMENT '根因告警ID',
    suppressed      INT             NULL DEFAULT 0                  COMMENT '是否被抑制 0=否 1=是',
    silenced        INT             NULL DEFAULT 0                  COMMENT '是否被静默 0=否 1=是',
    escalation_level TINYINT        NULL DEFAULT 0                  COMMENT '升级级别 0=原始 1=一级 2=二级',
    device_id       INT             NULL                            COMMENT '设备ID(冗余,加速关联查询)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报警消息表';

CREATE INDEX i_alarm_msg_log ON t_alarm_message (log_id);
CREATE INDEX i_alarm_msg_warn ON t_alarm_message (warn_id);
CREATE INDEX i_alarm_msg_time ON t_alarm_message (alarm_time);
CREATE INDEX i_alarm_msg_corr ON t_alarm_message (correlation_group);
CREATE INDEX i_alarm_msg_device ON t_alarm_message (device_id);

-- 报警日志表
CREATE TABLE IF NOT EXISTS t_error_message_log (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    alarm_rule_id   INT             NULL                            COMMENT '关联t_alarm_rule.id',
    asset_id        INT             NULL                            COMMENT '监测器(资产)id',
    monitor_name    VARCHAR(255)    NULL                            COMMENT '监测器名称',
    error_message   VARCHAR(1000)   NULL                            COMMENT '错误信息或警告条件',
    value           VARCHAR(20)     NULL                            COMMENT '当前监测值',
    state           INT             NOT NULL                        COMMENT '事件状态 1=错误 2=警告',
    warn_id         INT             NULL                            COMMENT '事件级别id',
    time            DATETIME        NOT NULL                        COMMENT '事件发生时间',
    end_time        DATETIME        NULL                            COMMENT '事件结束时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='报警日志表';

CREATE INDEX i_error_log_rule  ON t_error_message_log (alarm_rule_id);
CREATE INDEX i_error_log_asset ON t_error_message_log (asset_id);
CREATE INDEX i_error_log_time  ON t_error_message_log (time);

-- ============================================================================
-- 告警关联与疲劳治理
-- ============================================================================

-- 告警关联规则表
CREATE TABLE IF NOT EXISTS t_alarm_correlation_rule (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(100)    NOT NULL                        COMMENT '规则名称',
    device_id       INT             NULL                            COMMENT '目标设备ID(NULL=所有设备)',
    window_seconds  INT             NOT NULL DEFAULT 300            COMMENT '关联时间窗口(秒)',
    enabled         INT             NULL DEFAULT 1                  COMMENT '是否启用 1=启用 0=禁用',
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警关联规则表';

-- 告警升级策略表
CREATE TABLE IF NOT EXISTS t_alarm_escalation_policy (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(100)    NOT NULL                        COMMENT '策略名称',
    from_level      TINYINT         NOT NULL                        COMMENT '原始告警级别(1=WARNING/2=ERROR/3=FATAL)',
    to_level        TINYINT         NOT NULL                        COMMENT '升级后级别',
    timeout_minutes INT             NOT NULL                        COMMENT '超时分钟数',
    notify_type     VARCHAR(50)     NULL                            COMMENT '通知方式(site_notice/sms/phone)',
    enabled         INT             NULL DEFAULT 1                  COMMENT '是否启用 1=启用 0=禁用',
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警升级策略表';

-- 告警静默窗口表
CREATE TABLE IF NOT EXISTS t_alarm_silence_window (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(100)    NOT NULL                        COMMENT '静默窗口名称',
    device_id       INT             NULL                            COMMENT '目标设备ID(NULL=所有设备)',
    monitor_id      INT             NULL                            COMMENT '目标监控器ID(NULL=所有监控器)',
    start_time      DATETIME        NOT NULL                        COMMENT '静默开始时间',
    end_time        DATETIME        NOT NULL                        COMMENT '静默结束时间',
    reason          VARCHAR(500)    NULL                            COMMENT '静默原因',
    enabled         INT             NULL DEFAULT 1                  COMMENT '是否启用 1=启用 0=禁用',
    create_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP  COMMENT '创建时间',
    update_time     DATETIME        NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='告警静默窗口表';

-- ============================================================================
-- 联动
-- ============================================================================

-- 联动规则主表
CREATE TABLE IF NOT EXISTS t_linkage_rule (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '规则名称',
    cause_type      VARCHAR(20)     NOT NULL                        COMMENT '触发类型 ALARM|MONITOR',
    enabled         TINYINT         NOT NULL DEFAULT 1              COMMENT '是否启用 0=false 1=true',
    caption         VARCHAR(255)    NULL                            COMMENT '规则描述',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='联动规则主表';

-- 联动触发条件表
CREATE TABLE IF NOT EXISTS t_linkage_rule_cause (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    rule_id         INT             NOT NULL                        COMMENT '所属联动规则id',
    asset_id        INT             NOT NULL                        COMMENT '触发联动的监测器资产id',
    trigger_value   VARCHAR(63)     NOT NULL                        COMMENT '触发值',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='联动触发条件表';

CREATE INDEX i_linkage_cause_rule ON t_linkage_rule_cause (rule_id);
CREATE INDEX i_linkage_cause_asset ON t_linkage_rule_cause (asset_id);

-- 联动执行动作表
CREATE TABLE IF NOT EXISTS t_linkage_rule_effect (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    rule_id         INT             NOT NULL                        COMMENT '所属联动规则id',
    asset_id        INT             NOT NULL                        COMMENT '目标控制器资产id',
    command         VARCHAR(255)    NOT NULL                        COMMENT '执行命令(-1=切换)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='联动执行动作表';

CREATE INDEX i_linkage_effect_rule ON t_linkage_rule_effect (rule_id);
CREATE INDEX i_linkage_effect_asset ON t_linkage_rule_effect (asset_id);

-- 联动日志表
CREATE TABLE IF NOT EXISTS t_linkage_log (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    rule_id         INT             NOT NULL                        COMMENT '联动规则id',
    cause_monitor_id  INT           NULL                            COMMENT '原因监测器id',
    effect_monitor_id INT           NULL                            COMMENT '目标控制器id',
    time            DATETIME        NOT NULL                        COMMENT '联动触发时间',
    effect_command  VARCHAR(255)    NULL                            COMMENT '发送的指令值',
    success         TINYINT         NULL DEFAULT 1                  COMMENT '执行是否成功 0=false 1=true',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='联动日志表';

CREATE INDEX i_linkage_log_rule ON t_linkage_log (rule_id);
CREATE INDEX i_linkage_log_time ON t_linkage_log (time);

-- ============================================================================
-- 定时控制
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_scheduled_task (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '任务名称',
    control_id      INT             NOT NULL                        COMMENT '操控器Control资产id',
    command         VARCHAR(255)    NOT NULL                        COMMENT '发送给Control的命令',
    cron_expression VARCHAR(45)     NOT NULL                        COMMENT 'Spring 6字段cron表达式',
    enabled         TINYINT(1)      NOT NULL DEFAULT 1              COMMENT '1=启用 0=禁用',
    description     VARCHAR(255)    NULL                            COMMENT '任务描述',
    PRIMARY KEY (id),
    INDEX i_scheduled_task_control (control_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时控制任务表';

CREATE TABLE IF NOT EXISTS t_scheduled_task_log (
    id            BIGINT          NOT NULL AUTO_INCREMENT           COMMENT '主键id',
    task_id       INT             NOT NULL                          COMMENT '关联t_scheduled_task.id',
    task_name     VARCHAR(63)     NOT NULL                          COMMENT '任务名称(冗余)',
    control_id    INT             NOT NULL                          COMMENT '操控器Control资产id',
    command       VARCHAR(255)    NOT NULL                          COMMENT '执行命令',
    execute_time  BIGINT          NOT NULL                          COMMENT '执行时间(毫秒时间戳)',
    success       TINYINT(1)      NOT NULL                          COMMENT '1=成功 0=失败',
    error_message VARCHAR(500)    NULL                              COMMENT '错误信息(失败时)',
    PRIMARY KEY (id),
    INDEX i_task_log_task (task_id),
    INDEX i_task_log_time (execute_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='定时控制执行日志';

-- ============================================================================
-- 系统配置
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_system_setting (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    `config_key`           VARCHAR(127)    NOT NULL                        COMMENT '配置键',
    value           VARCHAR(500)    NULL                            COMMENT '配置值',
    description     VARCHAR(255)    NULL                            COMMENT '配置说明',
    PRIMARY KEY (id),
    UNIQUE INDEX uk_setting_key (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='系统配置表';

-- ============================================================================
-- 代码字典
-- ============================================================================

-- 代码分类目录表
CREATE TABLE IF NOT EXISTS t_code_catalog (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '分类名称',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码分类目录表';

-- 代码字典表
CREATE TABLE IF NOT EXISTS t_code_dict (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    catalog_id      INT             NOT NULL                        COMMENT '所属分类id',
    name            VARCHAR(63)     NOT NULL                        COMMENT '代码名称(分类内唯一)',
    caption         VARCHAR(255)    NULL                            COMMENT '显示名称',
    parent          INT             NULL                            COMMENT '父代码id(用于层级代码)',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='代码字典表';

CREATE INDEX i_code_dict_catalog ON t_code_dict (catalog_id);

-- ============================================================================
-- 事件级别
-- ============================================================================
CREATE TABLE IF NOT EXISTS t_event_rank (
    id              INT             NOT NULL AUTO_INCREMENT         COMMENT '事件等级主键',
    name            VARCHAR(255)    NULL                            COMMENT '事件等级名称',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='事件级别表';

-- ============================================================================
-- 运维模块 - 工单管理

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE IF NOT EXISTS t_asset_attribute (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    asset_id        INT             NOT NULL,
    attr_key        VARCHAR(100)    NOT NULL,
    attr_value      TEXT            NULL,
    attr_type       VARCHAR(50)     NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE INDEX i_attr_asset ON t_asset_attribute (asset_id);
CREATE INDEX i_attr_key ON t_asset_attribute (attr_key);
CREATE UNIQUE INDEX i_attr_asset_key ON t_asset_attribute (asset_id, attr_key);

-- ============================================================

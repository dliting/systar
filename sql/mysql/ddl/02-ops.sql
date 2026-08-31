-- ============================================================================

CREATE TABLE IF NOT EXISTS t_work_order (
    id                 BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    order_no           VARCHAR(32)     NOT NULL                        COMMENT '工单编号',
    title              VARCHAR(200)    NOT NULL                        COMMENT '工单标题',
    description        TEXT            NULL                            COMMENT '工单描述',
    type               VARCHAR(20)     NOT NULL                        COMMENT '类型: REPAIR/MAINTENANCE/INSPECTION/OTHER',
    source             VARCHAR(20)     NOT NULL                        COMMENT '来源: ALARM_AUTO/MANUAL/INSPECTION',
    alarm_message_id   INT             NULL                            COMMENT '关联告警消息ID',
    inspection_task_id BIGINT          NULL                            COMMENT '关联巡检任务ID',
    device_id          INT             NOT NULL                        COMMENT '关联设备ID',
    space_id           INT             NULL                            COMMENT '设备所属空间ID',
    priority           TINYINT         NOT NULL                        COMMENT '优先级: 1低/2中/3高/4紧急',
    status             VARCHAR(20)     NOT NULL DEFAULT 'CREATED'      COMMENT 'CREATED/ASSIGNED/PROCESSING/CLOSED/CANCELLED',
    assignee_id        BIGINT          NULL                            COMMENT '处理人ID',
    creator_id         BIGINT          NOT NULL                        COMMENT '创建人ID',
    closed_by          BIGINT          NULL                            COMMENT '关闭人ID',
    due_time           DATETIME        NULL                            COMMENT '处理截止时间(SLA)',
    resolution         TEXT            NULL                            COMMENT '处理结果',
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at          DATETIME        NULL,
    PRIMARY KEY (id),
    UNIQUE (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单表';

CREATE INDEX i_wo_status ON t_work_order (status);
CREATE INDEX i_wo_device ON t_work_order (device_id);
CREATE INDEX i_wo_space ON t_work_order (space_id);
CREATE INDEX i_wo_assignee ON t_work_order (assignee_id);
CREATE INDEX i_wo_alarm ON t_work_order (alarm_message_id);
CREATE INDEX i_wo_created ON t_work_order (created_at);

CREATE TABLE IF NOT EXISTS t_work_order_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    work_order_id   BIGINT          NOT NULL                        COMMENT '关联工单',
    operator_id     BIGINT          NOT NULL                        COMMENT '操作人',
    action          VARCHAR(20)     NOT NULL                        COMMENT 'CREATE/ASSIGN/PROCESS/CLOSE/CANCEL',
    comment         TEXT            NULL                            COMMENT '操作备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单操作日志表';

CREATE INDEX i_wol_order ON t_work_order_log (work_order_id);

CREATE TABLE IF NOT EXISTS t_work_order_attachment (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    work_order_id   BIGINT          NOT NULL                        COMMENT '关联工单',
    file_name       VARCHAR(255)    NOT NULL                        COMMENT '文件名',
    file_path       VARCHAR(500)    NOT NULL                        COMMENT '存储路径',
    file_size       BIGINT          NULL                            COMMENT '文件大小(字节)',
    uploaded_by     BIGINT          NOT NULL                        COMMENT '上传人ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='工单附件表';

CREATE INDEX i_woa_order ON t_work_order_attachment (work_order_id);

-- ============================================================================
-- 运维模块 - 设备台账
-- ============================================================================

CREATE TABLE IF NOT EXISTS t_device_attribute (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    device_id       INT             NOT NULL                        COMMENT '关联t_device.id',
    attr_key        VARCHAR(100)    NOT NULL                        COMMENT '属性名',
    attr_value      VARCHAR(500)    NULL                            COMMENT '属性值',
    attr_type       VARCHAR(10)     NULL                            COMMENT '值类型: STRING/NUMBER/DATE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (device_id, attr_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='设备扩展属性表';

CREATE INDEX i_da_device ON t_device_attribute (device_id);

CREATE TABLE IF NOT EXISTS t_maintenance_record (
    id                      BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    device_id               INT             NOT NULL                        COMMENT '关联设备',
    type                    VARCHAR(20)     NOT NULL                        COMMENT 'REPAIR/MAINTENANCE/INSPECTION/OTHER',
    title                   VARCHAR(200)    NOT NULL                        COMMENT '维护标题',
    description             TEXT            NULL                            COMMENT '维护描述',
    performer_id            BIGINT          NOT NULL                        COMMENT '执行人ID',
    creator_id              BIGINT          NOT NULL                        COMMENT '记录创建人ID',
    performed_at            DATETIME        NOT NULL                        COMMENT '执行时间',
    cost                    DECIMAL(10,2)   NULL                            COMMENT '费用',
    result                  VARCHAR(500)    NULL                            COMMENT '维护结论',
    next_maintenance_date   DATE            NULL                            COMMENT '下次维护日期',
    work_order_id           BIGINT          NULL                            COMMENT '关联工单',
    inspection_task_id      BIGINT          NULL                            COMMENT '关联巡检任务',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='维护记录表';

CREATE INDEX i_mr_device ON t_maintenance_record (device_id);
CREATE INDEX i_mr_type ON t_maintenance_record (type);
CREATE INDEX i_mr_performed ON t_maintenance_record (performed_at);
CREATE INDEX i_mr_next ON t_maintenance_record (next_maintenance_date);

CREATE TABLE IF NOT EXISTS t_maintenance_attachment (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    maintenance_id  BIGINT          NOT NULL                        COMMENT '关联维护记录',
    file_name       VARCHAR(255)    NOT NULL                        COMMENT '文件名',
    file_path       VARCHAR(500)    NOT NULL                        COMMENT '存储路径',
    file_size       BIGINT          NULL                            COMMENT '文件大小(字节)',
    uploaded_by     BIGINT          NOT NULL                        COMMENT '上传人ID',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='维护记录附件表';

CREATE INDEX i_ma_maintenance ON t_maintenance_attachment (maintenance_id);

-- ============================================================================
-- 运维模块 - 巡检管理
-- ============================================================================

CREATE TABLE IF NOT EXISTS t_inspection_plan (
    id                      BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    name                    VARCHAR(200)    NOT NULL                        COMMENT '计划名称',
    description             TEXT            NULL                            COMMENT '计划描述',
    cron_expression         VARCHAR(50)     NOT NULL                        COMMENT 'Cron表达式',
    enabled                 TINYINT         NOT NULL DEFAULT 1              COMMENT '是否启用',
    default_assignee_id     BIGINT          NULL                            COMMENT '默认巡检人',
    auto_create_workorder   TINYINT         NOT NULL DEFAULT 0              COMMENT '异常项是否自动创建工单',
    creator_id              BIGINT          NOT NULL                        COMMENT '创建人ID',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检计划表';

CREATE INDEX i_ip_enabled ON t_inspection_plan (enabled);

CREATE TABLE IF NOT EXISTS t_inspection_plan_device (
    id          BIGINT  NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    plan_id     BIGINT  NOT NULL                        COMMENT '关联计划',
    device_id   INT     NOT NULL                        COMMENT '关联设备',
    PRIMARY KEY (id),
    UNIQUE (plan_id, device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检计划设备关联表';

CREATE TABLE IF NOT EXISTS t_inspection_item_template (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    plan_id         BIGINT          NOT NULL                        COMMENT '关联计划',
    item_name       VARCHAR(200)    NOT NULL                        COMMENT '检查项名称',
    item_type       VARCHAR(20)     NOT NULL                        COMMENT 'CHECK/VALUE/TEXT',
    expected_value  VARCHAR(200)    NULL                            COMMENT '期望值/标准',
    sort_order      INT             NOT NULL DEFAULT 0              COMMENT '排序序号',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检检查项模板表';

CREATE INDEX i_iit_plan ON t_inspection_item_template (plan_id);

CREATE TABLE IF NOT EXISTS t_inspection_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    plan_id         BIGINT          NOT NULL                        COMMENT '关联计划',
    task_no         VARCHAR(32)     NOT NULL                        COMMENT '任务编号',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'      COMMENT 'PENDING/IN_PROGRESS/COMPLETED/CANCELLED',
    assignee_id     BIGINT          NULL                            COMMENT '巡检人ID',
    scheduled_time  DATETIME        NOT NULL                        COMMENT '计划执行时间',
    started_at      DATETIME        NULL                            COMMENT '实际开始时间',
    completed_at    DATETIME        NULL                            COMMENT '完成时间',
    remark          TEXT            NULL                            COMMENT '任务整体备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_no),
    UNIQUE (plan_id, scheduled_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检任务表';

CREATE INDEX i_it_plan ON t_inspection_task (plan_id);
CREATE INDEX i_it_status ON t_inspection_task (status);
CREATE INDEX i_it_assignee ON t_inspection_task (assignee_id);
CREATE INDEX i_it_scheduled ON t_inspection_task (scheduled_time);

CREATE TABLE IF NOT EXISTS t_inspection_result (
    id              BIGINT          NOT NULL AUTO_INCREMENT         COMMENT '主键id',
    task_id         BIGINT          NOT NULL                        COMMENT '关联任务',
    device_id       INT             NOT NULL                        COMMENT '关联设备',
    template_id     BIGINT          NOT NULL                        COMMENT '来源模板ID',
    item_name       VARCHAR(200)    NOT NULL                        COMMENT '检查项名称快照',
    expected_value  VARCHAR(200)    NULL                            COMMENT '期望值快照',
    check_result    VARCHAR(20)     NULL                            COMMENT 'NORMAL/ABNORMAL/SKIPPED',
    actual_value    VARCHAR(500)    NULL                            COMMENT '实际值',
    remark          TEXT            NULL                            COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE (task_id, device_id, template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='巡检结果表';

CREATE INDEX i_ir_task ON t_inspection_result (task_id);
CREATE INDEX i_ir_device ON t_inspection_result (device_id);
CREATE INDEX i_ir_created ON t_inspection_result (created_at);

-- ============================================================

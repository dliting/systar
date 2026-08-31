-- ============================================================================
-- 运维模块 - 工单管理
-- ============================================================================

CREATE TABLE IF NOT EXISTS t_work_order (
    id                 BIGINT          NOT NULL AUTO_INCREMENT,
    order_no           VARCHAR(32)     NOT NULL,
    title              VARCHAR(200)    NOT NULL,
    description        TEXT            NULL,
    type               VARCHAR(20)     NOT NULL,
    source             VARCHAR(20)     NOT NULL,
    alarm_message_id   INT             NULL,
    inspection_task_id BIGINT          NULL,
    device_id          INT             NOT NULL,
    space_id           INT             NULL,
    priority           TINYINT         NOT NULL,
    status             VARCHAR(20)     NOT NULL DEFAULT 'CREATED',
    assignee_id        BIGINT          NULL,
    creator_id         BIGINT          NOT NULL,
    closed_by          BIGINT          NULL,
    due_time           DATETIME        NULL,
    resolution         TEXT            NULL,
    created_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at          DATETIME        NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_work_order_no UNIQUE (order_no)
);

CREATE INDEX IF NOT EXISTS i_wo_status ON t_work_order (status);
CREATE INDEX IF NOT EXISTS i_wo_device ON t_work_order (device_id);
CREATE INDEX IF NOT EXISTS i_wo_space ON t_work_order (space_id);
CREATE INDEX IF NOT EXISTS i_wo_assignee ON t_work_order (assignee_id);
CREATE INDEX IF NOT EXISTS i_wo_alarm ON t_work_order (alarm_message_id);
CREATE INDEX IF NOT EXISTS i_wo_created ON t_work_order (created_at);

CREATE TABLE IF NOT EXISTS t_work_order_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    work_order_id   BIGINT          NOT NULL,
    operator_id     BIGINT          NOT NULL,
    action          VARCHAR(20)     NOT NULL,
    comment         TEXT            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_wol_order ON t_work_order_log (work_order_id);

CREATE TABLE IF NOT EXISTS t_work_order_attachment (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    work_order_id   BIGINT          NOT NULL,
    file_name       VARCHAR(255)    NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_size       BIGINT          NULL,
    uploaded_by     BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_woa_order ON t_work_order_attachment (work_order_id);

-- ============================================================================
-- 运维模块 - 设备台账
-- ============================================================================

CREATE TABLE IF NOT EXISTS t_device_attribute (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    device_id       INT             NOT NULL,
    attr_key        VARCHAR(100)    NOT NULL,
    attr_value      VARCHAR(500)    NULL,
    attr_type       VARCHAR(10)     NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_attr UNIQUE (device_id, attr_key)
);

CREATE INDEX IF NOT EXISTS i_da_device ON t_device_attribute (device_id);

CREATE TABLE IF NOT EXISTS t_maintenance_record (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    device_id               INT             NOT NULL,
    type                    VARCHAR(20)     NOT NULL,
    title                   VARCHAR(200)    NOT NULL,
    description             TEXT            NULL,
    performer_id            BIGINT          NOT NULL,
    creator_id              BIGINT          NOT NULL,
    performed_at            DATETIME        NOT NULL,
    cost                    DECIMAL(10,2)   NULL,
    result                  VARCHAR(500)    NULL,
    next_maintenance_date   DATE            NULL,
    work_order_id           BIGINT          NULL,
    inspection_task_id      BIGINT          NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_mr_device ON t_maintenance_record (device_id);
CREATE INDEX IF NOT EXISTS i_mr_type ON t_maintenance_record (type);
CREATE INDEX IF NOT EXISTS i_mr_performed ON t_maintenance_record (performed_at);
CREATE INDEX IF NOT EXISTS i_mr_next ON t_maintenance_record (next_maintenance_date);

CREATE TABLE IF NOT EXISTS t_maintenance_attachment (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    maintenance_id  BIGINT          NOT NULL,
    file_name       VARCHAR(255)    NOT NULL,
    file_path       VARCHAR(500)    NOT NULL,
    file_size       BIGINT          NULL,
    uploaded_by     BIGINT          NOT NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_ma_maintenance ON t_maintenance_attachment (maintenance_id);

-- ============================================================================
-- 运维模块 - 巡检管理
-- ============================================================================

CREATE TABLE IF NOT EXISTS t_inspection_plan (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    name                    VARCHAR(200)    NOT NULL,
    description             TEXT            NULL,
    cron_expression         VARCHAR(50)     NOT NULL,
    enabled                 TINYINT         NOT NULL DEFAULT 1,
    default_assignee_id     BIGINT          NULL,
    auto_create_workorder   TINYINT         NOT NULL DEFAULT 0,
    creator_id              BIGINT          NOT NULL,
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_ip_enabled ON t_inspection_plan (enabled);

CREATE TABLE IF NOT EXISTS t_inspection_plan_device (
    id          BIGINT  NOT NULL AUTO_INCREMENT,
    plan_id     BIGINT  NOT NULL,
    device_id   INT     NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_plan_device UNIQUE (plan_id, device_id)
);

CREATE TABLE IF NOT EXISTS t_inspection_item_template (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plan_id         BIGINT          NOT NULL,
    item_name       VARCHAR(200)    NOT NULL,
    item_type       VARCHAR(20)     NOT NULL,
    expected_value  VARCHAR(200)    NULL,
    sort_order      INT             NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS i_iit_plan ON t_inspection_item_template (plan_id);

CREATE TABLE IF NOT EXISTS t_inspection_task (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    plan_id         BIGINT          NOT NULL,
    task_no         VARCHAR(32)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assignee_id     BIGINT          NULL,
    scheduled_time  DATETIME        NOT NULL,
    started_at      DATETIME        NULL,
    completed_at    DATETIME        NULL,
    remark          TEXT            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_no UNIQUE (task_no),
    CONSTRAINT uk_plan_scheduled UNIQUE (plan_id, scheduled_time)
);

CREATE INDEX IF NOT EXISTS i_it_plan ON t_inspection_task (plan_id);
CREATE INDEX IF NOT EXISTS i_it_status ON t_inspection_task (status);
CREATE INDEX IF NOT EXISTS i_it_assignee ON t_inspection_task (assignee_id);
CREATE INDEX IF NOT EXISTS i_it_scheduled ON t_inspection_task (scheduled_time);

CREATE TABLE IF NOT EXISTS t_inspection_result (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    task_id         BIGINT          NOT NULL,
    device_id       INT             NOT NULL,
    template_id     BIGINT          NOT NULL,
    item_name       VARCHAR(200)    NOT NULL,
    expected_value  VARCHAR(200)    NULL,
    check_result    VARCHAR(20)     NULL,
    actual_value    VARCHAR(500)    NULL,
    remark          TEXT            NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_task_device_template UNIQUE (task_id, device_id, template_id)
);

CREATE INDEX IF NOT EXISTS i_ir_task ON t_inspection_result (task_id);
CREATE INDEX IF NOT EXISTS i_ir_device ON t_inspection_result (device_id);
CREATE INDEX IF NOT EXISTS i_ir_created ON t_inspection_result (created_at);

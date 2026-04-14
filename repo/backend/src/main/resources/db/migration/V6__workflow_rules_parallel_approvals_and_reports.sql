ALTER TABLE workflow_tasks
    ADD COLUMN current_approvals INT NOT NULL DEFAULT 0,
    ADD COLUMN approval_group_id UUID,
    ADD COLUMN parent_task_id UUID;

CREATE TABLE approval_tasks (
    id UUID PRIMARY KEY,
    parent_task_id UUID NOT NULL REFERENCES workflow_tasks(id) ON DELETE CASCADE,
    approval_group_id UUID NOT NULL,
    approver_role VARCHAR(32) NOT NULL,
    state VARCHAR(32) NOT NULL,
    assigned_user_id UUID REFERENCES users(id),
    lease_owner_user_id UUID REFERENCES users(id),
    lease_expires_at TIMESTAMP WITH TIME ZONE,
    decided_by_user_id UUID REFERENCES users(id),
    decision_comment VARCHAR(1000),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_approval_tasks_parent ON approval_tasks(parent_task_id);
CREATE INDEX idx_approval_tasks_group ON approval_tasks(approval_group_id);

CREATE TABLE workflow_rules (
    id UUID PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL,
    trigger_field VARCHAR(128) NOT NULL,
    expected_value VARCHAR(255) NOT NULL,
    next_task_types VARCHAR(500) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_workflow_rules_task_type ON workflow_rules(task_type, priority);

CREATE TABLE diagnostic_reports (
    id UUID PRIMARY KEY,
    report_type VARCHAR(64) NOT NULL,
    summary_json TEXT NOT NULL,
    trace_id VARCHAR(64),
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_diagnostic_reports_type_time ON diagnostic_reports(report_type, generated_at DESC);

CREATE TABLE password_reset_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_by_user_id UUID NOT NULL REFERENCES users(id),
    request_token VARCHAR(128) NOT NULL UNIQUE,
    temporary_password_plain VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed BOOLEAN NOT NULL DEFAULT FALSE,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

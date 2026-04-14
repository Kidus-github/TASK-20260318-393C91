CREATE TABLE reminder_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    route_id UUID NOT NULL REFERENCES routes(id),
    stop_id UUID NOT NULL REFERENCES stops(id),
    reservation_name VARCHAR(255) NOT NULL,
    scheduled_arrival_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reminder_at TIMESTAMP WITH TIME ZONE NOT NULL,
    checked_in_at TIMESTAMP WITH TIME ZONE,
    canceled BOOLEAN NOT NULL DEFAULT FALSE,
    reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
    missed_check_in_sent BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

ALTER TABLE workflow_tasks
    ADD COLUMN approval_mode VARCHAR(32) NOT NULL DEFAULT 'ANY',
    ADD COLUMN required_approvals INT NOT NULL DEFAULT 1,
    ADD COLUMN approval_count INT NOT NULL DEFAULT 0,
    ADD COLUMN progress_step INT NOT NULL DEFAULT 1,
    ADD COLUMN progress_total INT NOT NULL DEFAULT 1,
    ADD COLUMN escalated BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN resubmission_count INT NOT NULL DEFAULT 0;

CREATE TABLE system_alerts (
    id UUID PRIMARY KEY,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    summary VARCHAR(500) NOT NULL,
    details VARCHAR(4000),
    trace_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_reminder_subscriptions_due ON reminder_subscriptions(reminder_at, scheduled_arrival_at);
CREATE INDEX idx_system_alerts_type_created ON system_alerts(alert_type, created_at DESC);

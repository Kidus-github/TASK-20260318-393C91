CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    role_name VARCHAR(32) NOT NULL,
    temporary_password BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE recovery_codes (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash VARCHAR(255) NOT NULL,
    user_agent VARCHAR(255) NOT NULL,
    ip_hint VARCHAR(64),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE routes (
    id UUID PRIMARY KEY,
    route_number VARCHAR(32) NOT NULL,
    route_name VARCHAR(128) NOT NULL,
    frequency_priority INT NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE stops (
    id UUID PRIMARY KEY,
    stop_name VARCHAR(128) NOT NULL,
    stop_name_pinyin VARCHAR(128),
    stop_initials VARCHAR(32),
    keyword_blob VARCHAR(512),
    address VARCHAR(255),
    popularity INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE route_stops (
    route_id UUID NOT NULL REFERENCES routes(id) ON DELETE CASCADE,
    stop_id UUID NOT NULL REFERENCES stops(id) ON DELETE CASCADE,
    PRIMARY KEY (route_id, stop_id)
);

CREATE TABLE reminder_preferences (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    lead_minutes INT NOT NULL DEFAULT 10,
    dnd_start VARCHAR(5) NOT NULL DEFAULT '22:00',
    dnd_end VARCHAR(5) NOT NULL DEFAULT '07:00',
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(64) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    sensitivity_level VARCHAR(32) NOT NULL,
    read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE queued_messages (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(64) NOT NULL,
    payload VARCHAR(4000) NOT NULL,
    title VARCHAR(255) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    retry_count INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    sensitivity_level VARCHAR(32) NOT NULL,
    trace_id VARCHAR(64)
);

CREATE TABLE failed_messages (
    id UUID PRIMARY KEY,
    original_queue_id UUID NOT NULL,
    error_summary VARCHAR(500) NOT NULL,
    payload_hash VARCHAR(128) NOT NULL,
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    task_type VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    owner_role VARCHAR(32) NOT NULL,
    assigned_user_id UUID REFERENCES users(id),
    lease_owner_user_id UUID REFERENCES users(id),
    lease_expires_at TIMESTAMP,
    payload VARCHAR(4000),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE workflow_decisions (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL REFERENCES workflow_tasks(id) ON DELETE CASCADE,
    decided_by_user_id UUID NOT NULL REFERENCES users(id),
    decision VARCHAR(32) NOT NULL,
    comment_text VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE notification_templates (
    id UUID PRIMARY KEY,
    template_key VARCHAR(128) NOT NULL UNIQUE,
    title_template VARCHAR(255) NOT NULL,
    content_template VARCHAR(2000) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE search_weight_configs (
    id UUID PRIMARY KEY,
    exact_weight INT NOT NULL,
    prefix_weight INT NOT NULL,
    pinyin_weight INT NOT NULL,
    popularity_weight INT NOT NULL,
    frequency_weight INT NOT NULL,
    revision INT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE field_dictionaries (
    id UUID PRIMARY KEY,
    dictionary_type VARCHAR(64) NOT NULL,
    source_value VARCHAR(255) NOT NULL,
    standardized_value VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE parsing_templates (
    id UUID PRIMARY KEY,
    template_name VARCHAR(128) NOT NULL,
    template_type VARCHAR(32) NOT NULL,
    semantic_version VARCHAR(32) NOT NULL,
    revision INT NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    body TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE parsed_records (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES parsing_templates(id),
    status VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255) NOT NULL,
    normalized_payload TEXT NOT NULL,
    warning_message VARCHAR(1000),
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID,
    action_name VARCHAR(128) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(128) NOT NULL,
    details VARCHAR(4000),
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_stops_stop_name_trgm ON stops USING gin (stop_name gin_trgm_ops);
CREATE INDEX idx_stops_pinyin_trgm ON stops USING gin (stop_name_pinyin gin_trgm_ops);
CREATE INDEX idx_routes_number ON routes(route_number);

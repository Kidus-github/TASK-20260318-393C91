CREATE TABLE cleaning_rules (
    id UUID PRIMARY KEY,
    rule_key VARCHAR(128) NOT NULL UNIQUE,
    field_name VARCHAR(128) NOT NULL,
    pattern VARCHAR(255) NOT NULL,
    replacement_value VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_route_stops_route ON route_stops(route_id);
CREATE INDEX idx_route_stops_stop ON route_stops(stop_id);

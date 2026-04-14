ALTER TABLE parsed_records
    ADD COLUMN template_semantic_version VARCHAR(32),
    ADD COLUMN template_content_hash VARCHAR(128),
    ADD COLUMN cleaning_rule_snapshot TEXT,
    ADD COLUMN source_log TEXT;

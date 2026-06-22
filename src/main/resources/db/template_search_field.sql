CREATE TABLE IF NOT EXISTS template_search_field
(
    id BIGINT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    field_code VARCHAR(64),
    field_name VARCHAR(100),
    field_path VARCHAR(1000),
    field_type VARCHAR(30),
    fuzzy_flag TINYINT DEFAULT 0,
    unique_flag TINYINT DEFAULT 0,
    description VARCHAR(500),
    create_time DATETIME,
    update_time DATETIME,
    INDEX idx_template_id (template_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模板检索条件配置';

CREATE TABLE IF NOT EXISTS template_node_constraint (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '模板ID',
    node_path VARCHAR(500) NOT NULL COMMENT '节点路径',
    node_name VARCHAR(200) COMMENT '节点名称',
    data_type VARCHAR(50) COMMENT '数据类型: String/Number/Boolean/Enum/Date/Time/DateTime',
    required_flag TINYINT DEFAULT 0 COMMENT '是否必填',
    default_value VARCHAR(500) COMMENT '默认值',
    format_pattern VARCHAR(100) COMMENT '格式模式',
    enum_config TEXT COMMENT '枚举配置JSON',
    boolean_format VARCHAR(50) COMMENT '布尔格式: true_false/Y_N/1_0/是否',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_template_path (template_id, node_path)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

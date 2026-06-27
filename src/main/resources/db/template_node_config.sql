-- 模板节点配置表（循环节点配置）
CREATE TABLE IF NOT EXISTS template_node_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL COMMENT '模板ID',
    node_path VARCHAR(500) NOT NULL COMMENT '节点路径',
    node_name VARCHAR(200) COMMENT '节点名称',
    is_loop TINYINT DEFAULT 0 COMMENT '是否循环节点',
    loop_code VARCHAR(100) COMMENT '循环编码',
    loop_level INT DEFAULT 0 COMMENT '循环层级',
    parent_loop_code VARCHAR(100) COMMENT '父循环编码',
    enable_condition TINYINT DEFAULT 0 COMMENT '是否启用条件过滤',
    condition_expression VARCHAR(1000) COMMENT '条件表达式',
    loop_description VARCHAR(500) COMMENT '循环说明',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_template_id (template_id),
    UNIQUE KEY uk_template_path (template_id, node_path)
) ENGINE=InnoDB COMMENT='模板节点配置表';

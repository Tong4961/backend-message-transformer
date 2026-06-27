CREATE DATABASE IF NOT EXISTS `dsl` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `dsl`;

-- 转换器主表
CREATE TABLE IF NOT EXISTS converter (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '转换器编码',
    name VARCHAR(200) NOT NULL COMMENT '转换器名称',
    source_format VARCHAR(20) NOT NULL COMMENT '源格式: XML/JSON',
    target_format VARCHAR(20) NOT NULL COMMENT '目标格式: XML/JSON',
    version INT NOT NULL DEFAULT 1 COMMENT '版本号',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '状态: DRAFT/PUBLISHED/DISABLED',
    description TEXT COMMENT '描述',
    source_schema TEXT COMMENT '源结构定义(JSON)',
    target_schema TEXT COMMENT '目标结构定义(JSON)',
    source_sample TEXT COMMENT '源示例数据',
    target_sample TEXT COMMENT '目标示例数据',
    source_template_id BIGINT COMMENT '源模板ID',
    target_template_id BIGINT COMMENT '目标模板ID',
    created_by VARCHAR(100) COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) COMMENT '修改人',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_code (code),
    INDEX idx_status (status)
) ENGINE=InnoDB COMMENT='转换器表';

-- 映射规则表
CREATE TABLE IF NOT EXISTS mapping_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    converter_id BIGINT NOT NULL COMMENT '转换器ID',
    source_expression VARCHAR(500) COMMENT '源表达式(XPath/JsonPath)',
    target_expression VARCHAR(500) COMMENT '目标表达式(XPath/JsonPath)',
    source_type VARCHAR(50) COMMENT '源类型',
    target_type VARCHAR(50) COMMENT '目标类型',
    converter_chain VARCHAR(1000) COMMENT '转换函数链',
    default_value VARCHAR(500) COMMENT '默认值',
    null_policy VARCHAR(20) DEFAULT 'SKIP' COMMENT '空值策略: SKIP/NULL/EMPTY/DEFAULT/ERROR',
    condition_expression VARCHAR(1000) COMMENT '条件表达式',
    condition_value VARCHAR(500) COMMENT '条件满足时的赋值',
    priority INT DEFAULT 0 COMMENT '优先级',
    description VARCHAR(500) COMMENT '描述',
    mapping_type VARCHAR(20) DEFAULT 'DIRECT' COMMENT '映射类型: DIRECT/CONSTANT/FUNCTION/LOOP',
    loop_config TEXT COMMENT '循环配置JSON',
    enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_converter_id (converter_id),
    INDEX idx_priority (priority)
) ENGINE=InnoDB COMMENT='映射规则表';

-- 转换器版本表
CREATE TABLE IF NOT EXISTS converter_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    converter_id BIGINT NOT NULL COMMENT '转换器ID',
    version INT NOT NULL COMMENT '版本号',
    snapshot_data LONGTEXT COMMENT '快照数据(完整JSON)',
    created_by VARCHAR(100) COMMENT '创建人',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_converter_id (converter_id)
) ENGINE=InnoDB COMMENT='转换器版本表';

-- 模板表
CREATE TABLE IF NOT EXISTS template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '模板编码',
    name VARCHAR(200) NOT NULL COMMENT '模板名称',
    format VARCHAR(20) NOT NULL COMMENT '格式: XML/JSON',
    schema_data TEXT COMMENT '结构定义',
    sample_data TEXT COMMENT '示例数据',
    parent_id BIGINT COMMENT '父模板ID(继承)',
    description VARCHAR(500),
    created_by VARCHAR(100),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_code (code)
) ENGINE=InnoDB COMMENT='模板表';

-- 插件表
CREATE TABLE IF NOT EXISTS plugin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE COMMENT '插件编码',
    name VARCHAR(200) NOT NULL COMMENT '插件名称',
    type VARCHAR(50) NOT NULL COMMENT '插件类型: BUILTIN/CUSTOM',
    class_name VARCHAR(500) COMMENT '实现类名',
    config_schema TEXT COMMENT '配置Schema',
    description VARCHAR(500),
    enabled TINYINT DEFAULT 1,
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB COMMENT='插件表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NOT NULL COMMENT '追踪ID',
    converter_id BIGINT COMMENT '转换器ID',
    converter_code VARCHAR(100) COMMENT '转换器编码',
    converter_version INT COMMENT '版本',
    input_data LONGTEXT COMMENT '输入数据',
    output_data LONGTEXT COMMENT '输出数据',
    status VARCHAR(20) NOT NULL COMMENT '执行状态: SUCCESS/FAILED',
    error_message TEXT COMMENT '错误信息',
    duration BIGINT COMMENT '耗时(毫秒)',
    request_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '请求时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_converter_id (converter_id),
    INDEX idx_request_time (request_time)
) ENGINE=InnoDB COMMENT='审计日志表';

-- 字典表
CREATE TABLE IF NOT EXISTS dict_entry (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dict_code VARCHAR(100) NOT NULL COMMENT '字典编码',
    source_value VARCHAR(500) NOT NULL COMMENT '源值',
    target_value VARCHAR(500) NOT NULL COMMENT '目标值',
    description VARCHAR(500),
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_dict_code (dict_code)
) ENGINE=InnoDB COMMENT='字典映射表';

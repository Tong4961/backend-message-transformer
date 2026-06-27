-- 循环映射功能：mapping_rule 表新增字段
ALTER TABLE mapping_rule ADD COLUMN mapping_type VARCHAR(20) DEFAULT 'DIRECT' COMMENT '映射类型: DIRECT/CONSTANT/FUNCTION/LOOP';
ALTER TABLE mapping_rule ADD COLUMN loop_config TEXT COMMENT '循环配置JSON';

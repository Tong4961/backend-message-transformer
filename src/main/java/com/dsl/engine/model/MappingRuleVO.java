package com.dsl.engine.model;

import lombok.Data;

@Data
public class MappingRuleVO {
    private Long id;
    private String sourceExpression;
    private String targetExpression;
    private String sourceType;
    private String targetType;
    private String converterChain;
    private String defaultValue;
    private String nullPolicy;
    private String conditionExpression;
    private String conditionValue;
    private String mappingType;
    private String loopConfig;
}

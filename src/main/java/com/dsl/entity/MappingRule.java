package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("mapping_rule")
public class MappingRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long converterId;
    private String sourceExpression;
    private String targetExpression;
    private String sourceType;
    private String targetType;
    private String converterChain;
    private String defaultValue;
    private String nullPolicy;
    private String conditionExpression;
    private String conditionValue;
    private String description;
    private Integer enabled;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}

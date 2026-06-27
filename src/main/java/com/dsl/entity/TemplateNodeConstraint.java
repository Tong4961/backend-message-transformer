package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template_node_constraint")
public class TemplateNodeConstraint {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long templateId;
    private String nodePath;
    private String nodeName;
    private String dataType;
    private Boolean requiredFlag;
    private String defaultValue;
    private String formatPattern;
    private String enumConfig;
    private String booleanFormat;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

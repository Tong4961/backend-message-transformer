package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template_node_config")
public class TemplateNodeConfig {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long templateId;

    /** 节点路径 */
    private String nodePath;

    /** 节点名称 */
    private String nodeName;

    /** 是否循环节点 */
    private Boolean isLoop;

    /** 循环编码 */
    private String loopCode;

    /** 循环层级 */
    private Integer loopLevel;

    /** 父循环编码 */
    private String parentLoopCode;

    /** 是否启用条件过滤 */
    private Boolean enableCondition;

    /** 条件表达式 */
    private String conditionExpression;

    /** 循环说明 */
    private String loopDescription;

    private LocalDateTime createdTime;

    private LocalDateTime updatedTime;
}

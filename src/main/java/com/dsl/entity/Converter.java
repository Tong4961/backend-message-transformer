package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("converter")
public class Converter {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String sourceFormat;
    private String targetFormat;
    private Integer version;
    private String status;
    private String description;
    private String sourceSchema;
    private String targetSchema;
    private String sourceSample;
    private String targetSample;
    private Long sourceTemplateId;
    private Long targetTemplateId;
    private String createdBy;
    private LocalDateTime createdTime;
    private String updatedBy;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}

package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template")
public class Template {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private String format;
    private String schemaData;
    private String sampleData;
    private Long parentId;
    private String description;
    private String createdBy;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
    @TableLogic
    private Integer deleted;
}

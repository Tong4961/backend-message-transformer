package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("template_converter_version")
public class ConverterVersion {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long converterId;
    private Integer version;
    private String snapshotData;
    private String createdBy;
    private LocalDateTime createdTime;
}

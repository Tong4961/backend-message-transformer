package com.dsl.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String type; // TRANSFORM / CONFIG
    private String traceId;
    private Long converterId;
    private String converterCode;
    private Integer converterVersion;
    private String inputData;
    private String outputData;
    private String status;
    private String errorMessage;
    private Long duration;
    private String operation; // CREATE / UPDATE / DELETE
    private String targetType; // CONVERTER / RULE / TEMPLATE
    private String targetName;
    private String changeDetail; // 变更详情JSON
    private String operator;
    private LocalDateTime requestTime;
}

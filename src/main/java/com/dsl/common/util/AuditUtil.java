package com.dsl.common.util;

import com.dsl.entity.AuditLog;
import com.dsl.service.AuditService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class AuditUtil {

    private final AuditService auditService;

    public AuditUtil(AuditService auditService) {
        this.auditService = auditService;
    }

    public void logConfig(String operation, String targetType, String targetName,
                          Long converterId, String converterCode) {
        logConfig(operation, targetType, targetName, converterId, converterCode, null);
    }

    public void logConfig(String operation, String targetType, String targetName,
                          Long converterId, String converterCode, String changeDetail) {
        AuditLog log = new AuditLog();
        log.setType("CONFIG");
        log.setTraceId(UUID.randomUUID().toString().replace("-", ""));
        log.setOperation(operation);
        log.setTargetType(targetType);
        log.setTargetName(targetName);
        log.setConverterId(converterId);
        log.setConverterCode(converterCode);
        log.setChangeDetail(changeDetail);
        log.setStatus("SUCCESS");
        log.setOperator("system");
        log.setRequestTime(LocalDateTime.now());
        auditService.save(log);
    }
}

package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dsl.common.result.PageResult;
import com.dsl.entity.AuditLog;
import com.dsl.mapper.AuditLogMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void save(AuditLog log) {
        auditLogMapper.insert(log);
    }

    public PageResult<AuditLog> page(int page, int size, String type, String converterCode,
                                      String status) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (type != null && !type.isEmpty()) {
            wrapper.eq(AuditLog::getType, type);
        }
        if (converterCode != null && !converterCode.isEmpty()) {
            wrapper.eq(AuditLog::getConverterCode, converterCode);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AuditLog::getStatus, status);
        }
        wrapper.orderByDesc(AuditLog::getRequestTime);
        Page<AuditLog> result = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    public AuditLog getById(Long id) {
        return auditLogMapper.selectById(id);
    }
}

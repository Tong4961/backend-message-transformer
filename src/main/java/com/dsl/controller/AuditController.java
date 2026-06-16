package com.dsl.controller;

import com.dsl.common.result.PageResult;
import com.dsl.common.result.R;
import com.dsl.entity.AuditLog;
import com.dsl.service.AuditService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/page")
    public R<PageResult<AuditLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String converterCode,
            @RequestParam(required = false) String status) {
        return R.ok(auditService.page(page, size, type, converterCode, status));
    }

    @GetMapping("/{id}")
    public R<AuditLog> getById(@PathVariable Long id) {
        return R.ok(auditService.getById(id));
    }
}

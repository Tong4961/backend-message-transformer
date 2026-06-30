package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.service.ValidationService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/template")
public class ValidationController {

    @Autowired
    private ValidationService validationService;

    @PostMapping("/validate")
    public R<ValidationResult> validate(@RequestBody ValidateRequest request) {
        ValidationResult result = validationService.validate(
                Long.valueOf(request.getTemplateId()), request.getMessageType(), request.getContent());
        return R.ok(result);
    }

    @Data
    public static class ValidateRequest {
        private String templateId;
        private String messageType;
        private String content;
    }
}

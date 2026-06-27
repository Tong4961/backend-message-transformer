package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.entity.TemplateNodeConfig;
import com.dsl.service.TemplateNodeConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template-node-config")
public class TemplateNodeConfigController {

    private final TemplateNodeConfigService service;

    public TemplateNodeConfigController(TemplateNodeConfigService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public R<List<TemplateNodeConfig>> list(@RequestParam Long templateId) {
        return R.ok(service.getByTemplateId(templateId));
    }

    @PostMapping("/save")
    public R<Void> save(@RequestBody SaveRequest request) {
        service.saveConfigs(request.getTemplateId(), request.getConfigs());
        return R.ok();
    }

    public static class SaveRequest {
        private Long templateId;
        private List<TemplateNodeConfig> configs;

        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public List<TemplateNodeConfig> getConfigs() { return configs; }
        public void setConfigs(List<TemplateNodeConfig> configs) { this.configs = configs; }
    }

    @DeleteMapping("/{templateId}")
    public R<Void> delete(@PathVariable Long templateId) {
        service.deleteByTemplateId(templateId);
        return R.ok();
    }
}

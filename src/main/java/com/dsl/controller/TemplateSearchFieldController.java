package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.entity.TemplateSearchField;
import com.dsl.service.TemplateSearchFieldService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template-search-field")
public class TemplateSearchFieldController {

    private final TemplateSearchFieldService service;

    public TemplateSearchFieldController(TemplateSearchFieldService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public R<List<TemplateSearchField>> list(@RequestParam Long templateId) {
        return R.ok(service.getByTemplateId(templateId));
    }

    @PostMapping("/save")
    public R<Void> save(@RequestBody SaveRequest request) {
        service.saveFields(request.getTemplateId(), request.getFields());
        return R.ok();
    }

    public static class SaveRequest {
        private String templateId;
        private List<TemplateSearchField> fields;

        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public List<TemplateSearchField> getFields() { return fields; }
        public void setFields(List<TemplateSearchField> fields) { this.fields = fields; }
    }

    @DeleteMapping("/{templateId}")
    public R<Void> delete(@PathVariable Long templateId) {
        service.deleteByTemplateId(templateId);
        return R.ok();
    }
}

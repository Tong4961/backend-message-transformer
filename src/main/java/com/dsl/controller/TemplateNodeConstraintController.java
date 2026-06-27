package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.service.TemplateNodeConstraintService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template-node-constraint")
public class TemplateNodeConstraintController {

    private final TemplateNodeConstraintService service;

    public TemplateNodeConstraintController(TemplateNodeConstraintService service) {
        this.service = service;
    }

    @GetMapping("/list")
    public R<List<TemplateNodeConstraint>> list(@RequestParam Long templateId) {
        return R.ok(service.getByTemplateId(templateId));
    }

    @PostMapping("/save")
    public R<Void> save(@RequestBody SaveRequest request) {
        service.saveConstraints(request.getTemplateId(), request.getConstraints());
        return R.ok(null);
    }

    @DeleteMapping("/{templateId}")
    public R<Void> delete(@PathVariable Long templateId) {
        service.deleteByTemplateId(templateId);
        return R.ok(null);
    }

    public static class SaveRequest {
        private Long templateId;
        private List<TemplateNodeConstraint> constraints;

        public Long getTemplateId() { return templateId; }
        public void setTemplateId(Long templateId) { this.templateId = templateId; }
        public List<TemplateNodeConstraint> getConstraints() { return constraints; }
        public void setConstraints(List<TemplateNodeConstraint> constraints) { this.constraints = constraints; }
    }
}

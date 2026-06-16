package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.entity.Template;
import com.dsl.service.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/template")
public class TemplateController {

    private final TemplateService templateService;

    public TemplateController(TemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping("/list")
    public R<List<Template>> list() {
        return R.ok(templateService.list());
    }

    @GetMapping("/{id}")
    public R<Template> getById(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<Template> getByCode(@PathVariable String code) {
        return R.ok(templateService.getByCode(code));
    }

    @PostMapping
    public R<Template> create(@RequestBody Template template) {
        return R.ok(templateService.create(template));
    }

    @PutMapping
    public R<Template> update(@RequestBody Template template) {
        return R.ok(templateService.update(template));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        templateService.delete(id);
        return R.ok();
    }
}

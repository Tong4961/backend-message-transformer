package com.dsl.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dsl.common.result.R;
import com.dsl.engine.model.TreeNode;
import com.dsl.engine.parser.JsonSourceParser;
import com.dsl.engine.parser.XmlSourceParser;
import com.dsl.entity.Template;
import com.dsl.service.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/template")
public class TemplateController {

    private final TemplateService templateService;
    private final XmlSourceParser xmlSourceParser;
    private final JsonSourceParser jsonSourceParser;

    public TemplateController(TemplateService templateService,
                              XmlSourceParser xmlSourceParser,
                              JsonSourceParser jsonSourceParser) {
        this.templateService = templateService;
        this.xmlSourceParser = xmlSourceParser;
        this.jsonSourceParser = jsonSourceParser;
    }

    @GetMapping("/list")
    public R<List<Template>> list(@RequestParam(required = false) String format) {
        if (format != null && !format.isEmpty()) {
            return R.ok(templateService.listByFormat(format));
        }
        return R.ok(templateService.list());
    }

    @GetMapping("/page")
    public R<Page<Template>> page(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String keyword) {
        return R.ok(templateService.page(page, size, format, keyword));
    }

    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(templateService.countByFormat());
    }

    @GetMapping("/{id}")
    public R<Template> getById(@PathVariable Long id) {
        return R.ok(templateService.getById(id));
    }

    @GetMapping("/{id}/structure")
    public R<List<TreeNode>> getStructure(@PathVariable Long id) {
        Template template = templateService.getById(id);
        if (template == null) {
            return R.fail("模板不存在");
        }
        String data = template.getSchemaData();
        if (data == null || data.isEmpty()) {
            data = template.getSampleData();
        }
        if (data == null || data.isEmpty()) {
            return R.ok(Collections.emptyList());
        }
        List<TreeNode> nodes;
        if ("JSON".equalsIgnoreCase(template.getFormat())) {
            nodes = jsonSourceParser.parseStructure(data);
        } else {
            nodes = xmlSourceParser.parseStructure(data);
        }
        return R.ok(nodes);
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

    @GetMapping("/{id}/export")
    public R<Map<String, Object>> export(@PathVariable Long id) {
        Template template = templateService.getById(id);
        if (template == null) {
            return R.fail("模板不存在");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("code", template.getCode());
        data.put("name", template.getName());
        data.put("format", template.getFormat());
        data.put("description", template.getDescription());
        data.put("tags", template.getTags());
        data.put("schemaData", template.getSchemaData());
        data.put("sampleData", template.getSampleData());
        return R.ok(data);
    }

    @PostMapping("/import")
    public R<Template> importTemplate(@RequestBody Map<String, Object> data) {
        Template template = new Template();
        template.setCode((String) data.get("code"));
        template.setName((String) data.get("name"));
        template.setFormat((String) data.get("format"));
        template.setDescription((String) data.get("description"));
        template.setTags((String) data.get("tags"));
        template.setSchemaData((String) data.get("schemaData"));
        template.setSampleData((String) data.get("sampleData"));
        return R.ok(templateService.createOrUpdate(template));
    }
}

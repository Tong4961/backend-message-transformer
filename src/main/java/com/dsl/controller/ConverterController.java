package com.dsl.controller;

import com.dsl.common.result.PageResult;
import com.dsl.common.result.R;
import com.dsl.entity.Converter;
import com.dsl.entity.ConverterVersion;
import com.dsl.service.ConverterService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/converter")
public class ConverterController {

    private final ConverterService converterService;

    public ConverterController(ConverterService converterService) {
        this.converterService = converterService;
    }

    @GetMapping("/page")
    public R<PageResult<Converter>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return R.ok(converterService.page(page, size, keyword, status));
    }

    @GetMapping("/{id}")
    public R<Converter> getById(@PathVariable Long id) {
        return R.ok(converterService.getById(id));
    }

    @GetMapping("/code/{code}")
    public R<Converter> getByCode(@PathVariable String code) {
        return R.ok(converterService.getByCode(code));
    }

    @PostMapping
    public R<Converter> create(@RequestBody Converter converter) {
        return R.ok(converterService.create(converter));
    }

    @PutMapping
    public R<Converter> update(@RequestBody Converter converter,
                               @RequestParam(defaultValue = "true") boolean logAudit) {
        return R.ok(converterService.update(converter, logAudit));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        converterService.delete(id);
        return R.ok();
    }

    @PostMapping("/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        converterService.publish(id);
        return R.ok();
    }

    @PostMapping("/{id}/disable")
    public R<Void> disable(@PathVariable Long id) {
        converterService.disable(id);
        return R.ok();
    }

    @PostMapping("/{id}/clone")
    public R<Converter> clone(@PathVariable Long id) {
        return R.ok(converterService.clone(id));
    }

    @GetMapping("/{id}/export")
    public R<String> export(@PathVariable Long id) {
        return R.ok(converterService.exportConverter(id));
    }

    @PostMapping("/import")
    public R<Converter> importConverter(@RequestBody String jsonData) {
        return R.ok(converterService.importConverter(jsonData));
    }

    @GetMapping("/{id}/versions")
    public R<List<ConverterVersion>> versions(@PathVariable Long id) {
        return R.ok(converterService.getVersions(id));
    }

    @PostMapping("/{id}/rollback/{version}")
    public R<Void> rollback(@PathVariable Long id, @PathVariable Integer version) {
        converterService.rollback(id, version);
        return R.ok();
    }
}

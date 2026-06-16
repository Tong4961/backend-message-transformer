package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.entity.MappingRule;
import com.dsl.service.MappingRuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mapping-rule")
public class MappingRuleController {

    private final MappingRuleService mappingRuleService;

    public MappingRuleController(MappingRuleService mappingRuleService) {
        this.mappingRuleService = mappingRuleService;
    }

    @GetMapping("/list/{converterId}")
    public R<List<MappingRule>> list(@PathVariable Long converterId) {
        return R.ok(mappingRuleService.getByConverterId(converterId));
    }

    @PostMapping("/save/{converterId}")
    public R<Void> saveRules(@PathVariable Long converterId, @RequestBody List<MappingRule> rules) {
        mappingRuleService.saveRules(converterId, rules);
        return R.ok();
    }

    @PostMapping
    public R<MappingRule> create(@RequestBody MappingRule rule) {
        return R.ok(mappingRuleService.createRule(rule));
    }

    @PutMapping
    public R<MappingRule> update(@RequestBody MappingRule rule) {
        return R.ok(mappingRuleService.updateRule(rule));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        mappingRuleService.deleteRule(id);
        return R.ok();
    }
}

package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsl.common.util.AuditUtil;
import com.dsl.entity.Converter;
import com.dsl.entity.MappingRule;
import com.dsl.mapper.ConverterMapper;
import com.dsl.mapper.MappingRuleMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

@Service
public class MappingRuleService {

    private final MappingRuleMapper mappingRuleMapper;
    private final ConverterMapper converterMapper;
    private final AuditUtil auditUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MappingRuleService(MappingRuleMapper mappingRuleMapper, ConverterMapper converterMapper,
                              AuditUtil auditUtil) {
        this.mappingRuleMapper = mappingRuleMapper;
        this.converterMapper = converterMapper;
        this.auditUtil = auditUtil;
    }

    public List<MappingRule> getByConverterId(Long converterId) {
        return mappingRuleMapper.selectList(
            new LambdaQueryWrapper<MappingRule>()
                .eq(MappingRule::getConverterId, converterId));
    }

    @Transactional
    public void saveRules(Long converterId, List<MappingRule> rules) {
        // Get old rules for comparison
        List<MappingRule> oldRules = mappingRuleMapper.selectList(
            new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, converterId));

        // Delete existing rules
        mappingRuleMapper.delete(
            new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, converterId));

        // Insert new rules
        for (int i = 0; i < rules.size(); i++) {
            MappingRule rule = rules.get(i);
            rule.setId(null);
            rule.setConverterId(converterId);
            rule.setCreatedTime(LocalDateTime.now());
            rule.setUpdatedTime(LocalDateTime.now());
            mappingRuleMapper.insert(rule);
        }

        // Build change detail
        Converter converter = converterMapper.selectById(converterId);
        if (converter != null) {
            String changeDetail = buildChangeDetail(oldRules, rules);
            auditUtil.logConfig("UPDATE", "RULE", "映射规则(" + rules.size() + "条)",
                converterId, converter.getCode(), changeDetail);
        }
    }

    private String buildChangeDetail(List<MappingRule> oldRules, List<MappingRule> newRules) {
        try {
            Map<String, Object> detail = new HashMap<>();
            detail.put("oldCount", oldRules.size());
            detail.put("newCount", newRules.size());

            // Build key -> rule maps for comparison
            Map<String, MappingRule> oldMap = new LinkedHashMap<>();
            for (MappingRule r : oldRules) {
                String key = r.getSourceExpression() + " -> " + r.getTargetExpression();
                oldMap.put(key, r);
            }

            Map<String, MappingRule> newMap = new LinkedHashMap<>();
            for (MappingRule r : newRules) {
                String key = r.getSourceExpression() + " -> " + r.getTargetExpression();
                newMap.put(key, r);
            }

            // Find added rules
            List<Map<String, String>> added = new ArrayList<>();
            for (Map.Entry<String, MappingRule> entry : newMap.entrySet()) {
                if (!oldMap.containsKey(entry.getKey())) {
                    added.add(ruleToMap(entry.getValue()));
                }
            }

            // Find deleted rules
            List<Map<String, String>> deleted = new ArrayList<>();
            for (Map.Entry<String, MappingRule> entry : oldMap.entrySet()) {
                if (!newMap.containsKey(entry.getKey())) {
                    deleted.add(ruleToMap(entry.getValue()));
                }
            }

            // Find modified rules
            List<Map<String, Object>> modified = new ArrayList<>();
            for (Map.Entry<String, MappingRule> entry : newMap.entrySet()) {
                MappingRule oldRule = oldMap.get(entry.getKey());
                if (oldRule != null) {
                    MappingRule newRule = entry.getValue();
                    Map<String, String> changes = new HashMap<>();
                    if (!Objects.equals(oldRule.getConverterChain(), newRule.getConverterChain())) {
                        changes.put("converterChain", oldRule.getConverterChain() + " → " + newRule.getConverterChain());
                    }
                    if (!Objects.equals(oldRule.getConditionExpression(), newRule.getConditionExpression())) {
                        changes.put("condition", oldRule.getConditionExpression() + " → " + newRule.getConditionExpression());
                    }
                    if (!Objects.equals(oldRule.getConditionValue(), newRule.getConditionValue())) {
                        changes.put("conditionValue", oldRule.getConditionValue() + " → " + newRule.getConditionValue());
                    }
                    if (!Objects.equals(oldRule.getDefaultValue(), newRule.getDefaultValue())) {
                        changes.put("defaultValue", oldRule.getDefaultValue() + " → " + newRule.getDefaultValue());
                    }
                    if (!Objects.equals(oldRule.getNullPolicy(), newRule.getNullPolicy())) {
                        changes.put("nullPolicy", oldRule.getNullPolicy() + " → " + newRule.getNullPolicy());
                    }
                    if (!changes.isEmpty()) {
                        Map<String, Object> m = new HashMap<>();
                        m.put("rule", entry.getKey());
                        m.put("changes", changes);
                        modified.add(m);
                    }
                }
            }

            detail.put("added", added);
            detail.put("deleted", deleted);
            detail.put("modified", modified);
            return objectMapper.writeValueAsString(detail);
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, String> ruleToMap(MappingRule r) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("source", r.getSourceExpression());
        m.put("target", r.getTargetExpression());
        if (r.getConverterChain() != null && !r.getConverterChain().isEmpty()) {
            m.put("converter", r.getConverterChain());
        }
        if (r.getConditionExpression() != null && !r.getConditionExpression().isEmpty()) {
            m.put("condition", r.getConditionExpression());
        }
        return m;
    }

    @Transactional
    public MappingRule createRule(MappingRule rule) {
        rule.setCreatedTime(LocalDateTime.now());
        rule.setUpdatedTime(LocalDateTime.now());
        mappingRuleMapper.insert(rule);
        return rule;
    }

    @Transactional
    public MappingRule updateRule(MappingRule rule) {
        rule.setUpdatedTime(LocalDateTime.now());
        mappingRuleMapper.updateById(rule);
        return rule;
    }

    @Transactional
    public void deleteRule(Long id) {
        mappingRuleMapper.deleteById(id);
    }
}

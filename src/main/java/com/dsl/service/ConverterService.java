package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dsl.common.enums.ConverterStatus;
import com.dsl.common.exception.BizException;
import com.dsl.common.result.PageResult;
import com.dsl.common.util.AuditUtil;
import com.dsl.entity.Converter;
import com.dsl.entity.ConverterVersion;
import com.dsl.entity.MappingRule;
import com.dsl.mapper.ConverterMapper;
import com.dsl.mapper.ConverterVersionMapper;
import com.dsl.mapper.MappingRuleMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ConverterService {

    private final ConverterMapper converterMapper;
    private final MappingRuleMapper mappingRuleMapper;
    private final ConverterVersionMapper versionMapper;
    private final AuditUtil auditUtil;
    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ConverterService(ConverterMapper converterMapper, MappingRuleMapper mappingRuleMapper,
                           ConverterVersionMapper versionMapper, AuditUtil auditUtil) {
        this.converterMapper = converterMapper;
        this.mappingRuleMapper = mappingRuleMapper;
        this.versionMapper = versionMapper;
        this.auditUtil = auditUtil;
    }

    public PageResult<Converter> page(int page, int size, String keyword, String status) {
        LambdaQueryWrapper<Converter> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Converter::getName, keyword).or().like(Converter::getCode, keyword));
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Converter::getStatus, status);
        }
        wrapper.orderByDesc(Converter::getUpdatedTime);
        Page<Converter> result = converterMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), result.getRecords());
    }

    public Converter getById(Long id) {
        return converterMapper.selectById(id);
    }

    public Converter getByCode(String code) {
        return converterMapper.selectOne(
            new LambdaQueryWrapper<Converter>().eq(Converter::getCode, code));
    }

    @Transactional
    public Converter create(Converter converter) {
        // Check code uniqueness (active records only, logic delete auto-filtered)
        Converter existing = getByCode(converter.getCode());
        if (existing != null) {
            throw new BizException("转换器编码已存在: " + converter.getCode());
        }

        // Physical delete any soft-deleted records with same code to avoid unique key conflict
        converterMapper.physicalDeleteByCode(converter.getCode());

        converter.setVersion(1);
        converter.setStatus(ConverterStatus.DRAFT.name());
        converter.setCreatedTime(LocalDateTime.now());
        converter.setUpdatedTime(LocalDateTime.now());
        converterMapper.insert(converter);
        auditUtil.logConfig("CREATE", "CONVERTER", converter.getName(), converter.getId(), converter.getCode());
        return converter;
    }

    @Transactional
    public Converter update(Converter converter) {
        return update(converter, true);
    }

    @Transactional
    public Converter update(Converter converter, boolean logAudit) {
        Converter existing = converterMapper.selectById(converter.getId());
        if (existing == null) {
            throw new BizException("转换器不存在");
        }
        if (ConverterStatus.PUBLISHED.name().equals(existing.getStatus())) {
            throw new BizException("已发布的转换器不能直接修改，请先禁用或创建新版本");
        }
        converter.setUpdatedTime(LocalDateTime.now());
        converterMapper.updateById(converter);

        if (logAudit) {
            auditUtil.logConfig("UPDATE", "CONVERTER", existing.getName(), converter.getId(), existing.getCode());
        }
        return converter;
    }

    @Transactional
    public void delete(Long id) {
        Converter converter = converterMapper.selectById(id);
        if (converter == null) return;
        if (ConverterStatus.PUBLISHED.name().equals(converter.getStatus())) {
            throw new BizException("已发布的转换器不能删除");
        }
        converterMapper.deleteById(id);
        mappingRuleMapper.delete(new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, id));
        auditUtil.logConfig("DELETE", "CONVERTER", converter.getName(), id, converter.getCode());
    }

    @Transactional
    public void publish(Long id) {
        Converter converter = converterMapper.selectById(id);
        if (converter == null) throw new BizException("转换器不存在");

        // Save version snapshot
        saveVersion(converter);

        converter.setStatus(ConverterStatus.PUBLISHED.name());
        converter.setVersion(converter.getVersion() + 1);
        converter.setUpdatedTime(LocalDateTime.now());
        converterMapper.updateById(converter);
    }

    @Transactional
    public void disable(Long id) {
        Converter converter = converterMapper.selectById(id);
        if (converter == null) throw new BizException("转换器不存在");
        converter.setStatus(ConverterStatus.DISABLED.name());
        converter.setUpdatedTime(LocalDateTime.now());
        converterMapper.updateById(converter);
    }

    @Transactional
    public Converter clone(Long id) {
        Converter original = converterMapper.selectById(id);
        if (original == null) throw new BizException("转换器不存在");

        Converter clone = new Converter();
        clone.setCode(original.getCode() + "_copy_" + System.currentTimeMillis());
        clone.setName(original.getName() + "(副本)");
        clone.setSourceFormat(original.getSourceFormat());
        clone.setTargetFormat(original.getTargetFormat());
        clone.setDescription(original.getDescription());
        clone.setSourceSchema(original.getSourceSchema());
        clone.setTargetSchema(original.getTargetSchema());
        clone.setSourceSample(original.getSourceSample());
        clone.setTargetSample(original.getTargetSample());
        clone.setVersion(1);
        clone.setStatus(ConverterStatus.DRAFT.name());
        clone.setCreatedTime(LocalDateTime.now());
        clone.setUpdatedTime(LocalDateTime.now());
        converterMapper.insert(clone);

        // Clone mapping rules
        List<MappingRule> rules = mappingRuleMapper.selectList(
            new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, id));
        for (MappingRule rule : rules) {
            rule.setId(null);
            rule.setConverterId(clone.getId());
            rule.setCreatedTime(LocalDateTime.now());
            rule.setUpdatedTime(LocalDateTime.now());
            mappingRuleMapper.insert(rule);
        }

        return clone;
    }

    public String exportConverter(Long id) {
        Converter converter = converterMapper.selectById(id);
        if (converter == null) throw new BizException("转换器不存在");

        List<MappingRule> rules = mappingRuleMapper.selectList(
            new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, id));

        Map<String, Object> export = new HashMap<>();
        export.put("converter", converter);
        export.put("rules", rules);

        try {
            return objectMapper.writeValueAsString(export);
        } catch (Exception e) {
            throw new BizException("导出失败: " + e.getMessage());
        }
    }

    @Transactional
    public Converter importConverter(String jsonData) {
        try {
            Map<String, Object> data = objectMapper.readValue(jsonData,
                new TypeReference<Map<String, Object>>() {});

            Map<String, Object> convMap = (Map<String, Object>) data.get("converter");
            Converter converter = objectMapper.convertValue(convMap, Converter.class);
            converter.setId(null);
            converter.setCode(converter.getCode() + "_import_" + System.currentTimeMillis());
            converter.setVersion(1);
            converter.setStatus(ConverterStatus.DRAFT.name());
            converter.setCreatedTime(LocalDateTime.now());
            converter.setUpdatedTime(LocalDateTime.now());
            converterMapper.insert(converter);

            List<Map<String, Object>> ruleList = (List<Map<String, Object>>) data.get("rules");
            if (ruleList != null) {
                for (Map<String, Object> ruleMap : ruleList) {
                    MappingRule rule = objectMapper.convertValue(ruleMap, MappingRule.class);
                    rule.setId(null);
                    rule.setConverterId(converter.getId());
                    rule.setCreatedTime(LocalDateTime.now());
                    rule.setUpdatedTime(LocalDateTime.now());
                    mappingRuleMapper.insert(rule);
                }
            }

            return converter;
        } catch (Exception e) {
            throw new BizException("导入失败: " + e.getMessage());
        }
    }

    public List<ConverterVersion> getVersions(Long converterId) {
        return versionMapper.selectList(
            new LambdaQueryWrapper<ConverterVersion>()
                .eq(ConverterVersion::getConverterId, converterId)
                .orderByDesc(ConverterVersion::getVersion));
    }

    @Transactional
    public void rollback(Long converterId, Integer version) {
        ConverterVersion ver = versionMapper.selectOne(
            new LambdaQueryWrapper<ConverterVersion>()
                .eq(ConverterVersion::getConverterId, converterId)
                .eq(ConverterVersion::getVersion, version));
        if (ver == null) throw new BizException("版本不存在");

        try {
            Map<String, Object> data = objectMapper.readValue(ver.getSnapshotData(),
                new TypeReference<Map<String, Object>>() {});
            Map<String, Object> convMap = (Map<String, Object>) data.get("converter");
            Converter converter = objectMapper.convertValue(convMap, Converter.class);
            converter.setId(converterId);
            converter.setUpdatedTime(LocalDateTime.now());
            converterMapper.updateById(converter);

            // Replace rules
            mappingRuleMapper.delete(new LambdaQueryWrapper<MappingRule>()
                .eq(MappingRule::getConverterId, converterId));
            List<Map<String, Object>> ruleList = (List<Map<String, Object>>) data.get("rules");
            if (ruleList != null) {
                for (Map<String, Object> ruleMap : ruleList) {
                    MappingRule rule = objectMapper.convertValue(ruleMap, MappingRule.class);
                    rule.setId(null);
                    rule.setConverterId(converterId);
                    rule.setCreatedTime(LocalDateTime.now());
                    rule.setUpdatedTime(LocalDateTime.now());
                    mappingRuleMapper.insert(rule);
                }
            }
        } catch (Exception e) {
            throw new BizException("回滚失败: " + e.getMessage());
        }
    }

    private void saveVersion(Converter converter) {
        try {
            List<MappingRule> rules = mappingRuleMapper.selectList(
                new LambdaQueryWrapper<MappingRule>().eq(MappingRule::getConverterId, converter.getId()));

            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("converter", converter);
            snapshot.put("rules", rules);

            ConverterVersion version = new ConverterVersion();
            version.setConverterId(converter.getId());
            version.setVersion(converter.getVersion());
            version.setSnapshotData(objectMapper.writeValueAsString(snapshot));
            version.setCreatedBy("system");
            version.setCreatedTime(LocalDateTime.now());
            versionMapper.insert(version);
        } catch (Exception e) {
            throw new BizException("保存版本快照失败: " + e.getMessage());
        }
    }
}

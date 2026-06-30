package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dsl.common.exception.BizException;
import com.dsl.entity.Template;
import com.dsl.entity.TemplateNodeConfig;
import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.entity.TemplateSearchField;
import com.dsl.mapper.TemplateMapper;
import com.dsl.mapper.TemplateNodeConfigMapper;
import com.dsl.mapper.TemplateNodeConstraintMapper;
import com.dsl.mapper.TemplateSearchFieldMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TemplateService {

    private final TemplateMapper templateMapper;
    private final TemplateNodeConstraintMapper constraintMapper;
    private final TemplateNodeConfigMapper nodeConfigMapper;
    private final TemplateSearchFieldMapper searchFieldMapper;

    public TemplateService(TemplateMapper templateMapper,
                           TemplateNodeConstraintMapper constraintMapper,
                           TemplateNodeConfigMapper nodeConfigMapper,
                           TemplateSearchFieldMapper searchFieldMapper) {
        this.templateMapper = templateMapper;
        this.constraintMapper = constraintMapper;
        this.nodeConfigMapper = nodeConfigMapper;
        this.searchFieldMapper = searchFieldMapper;
    }

    public List<Template> list() {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Template::getUpdatedTime);
        return templateMapper.selectList(wrapper);
    }

    public List<Template> listByFormat(String format) {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        if (format != null && !format.isEmpty()) {
            wrapper.eq(Template::getFormat, format);
        }
        wrapper.orderByDesc(Template::getUpdatedTime);
        return templateMapper.selectList(wrapper);
    }

    public Page<Template> page(Integer page, Integer size, String format, String keyword) {
        Page<Template> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        if (format != null && !format.isEmpty()) {
            wrapper.eq(Template::getFormat, format);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                .like(Template::getCode, keyword)
                .or().like(Template::getName, keyword)
                .or().like(Template::getDescription, keyword)
                .or().like(Template::getTags, keyword)
            );
        }
        wrapper.orderByDesc(Template::getUpdatedTime);
        return templateMapper.selectPage(pageParam, wrapper);
    }

    public Map<String, Long> countByFormat() {
        Map<String, Long> stats = new HashMap<>();
        List<Template> all = templateMapper.selectList(null);
        stats.put("total", (long) all.size());
        stats.put("HL7_V3", all.stream().filter(t -> "HL7_V3".equals(t.getFormat())).count());
        stats.put("XML", all.stream().filter(t -> "XML".equals(t.getFormat())).count());
        stats.put("JSON", all.stream().filter(t -> "JSON".equals(t.getFormat())).count());
        return stats;
    }

    public Template getById(Long id) {
        return templateMapper.selectById(id);
    }

    public Template getByCode(String code) {
        return templateMapper.selectOne(
            new LambdaQueryWrapper<Template>().eq(Template::getCode, code));
    }

    @Transactional
    public Template create(Template template) {
        Template existing = getByCode(template.getCode());
        if (existing != null) {
            throw new BizException("模板编码已存在: " + template.getCode());
        }
        template.setCreatedTime(LocalDateTime.now());
        template.setUpdatedTime(LocalDateTime.now());
        templateMapper.insert(template);
        return template;
    }

    @Transactional
    public Template update(Template template) {
        template.setUpdatedTime(LocalDateTime.now());
        templateMapper.updateById(template);
        return template;
    }

    @Transactional
    public Template createOrUpdate(Template template) {
        Template existing = getByCode(template.getCode());
        if (existing != null) {
            template.setId(existing.getId());
            template.setCreatedTime(existing.getCreatedTime());
            template.setUpdatedTime(LocalDateTime.now());
            templateMapper.updateById(template);
            return template;
        }
        template.setCreatedTime(LocalDateTime.now());
        template.setUpdatedTime(LocalDateTime.now());
        templateMapper.insert(template);
        return template;
    }

    @Transactional
    public void delete(Long id) {
        constraintMapper.delete(new LambdaQueryWrapper<TemplateNodeConstraint>()
                .eq(TemplateNodeConstraint::getTemplateId, id));
        nodeConfigMapper.delete(new LambdaQueryWrapper<TemplateNodeConfig>()
                .eq(TemplateNodeConfig::getTemplateId, id));
        searchFieldMapper.delete(new LambdaQueryWrapper<TemplateSearchField>()
                .eq(TemplateSearchField::getTemplateId, id));
        templateMapper.deleteById(id);
    }
}

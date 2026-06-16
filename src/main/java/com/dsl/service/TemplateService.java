package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsl.common.exception.BizException;
import com.dsl.entity.Template;
import com.dsl.mapper.TemplateMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateService {

    private final TemplateMapper templateMapper;

    public TemplateService(TemplateMapper templateMapper) {
        this.templateMapper = templateMapper;
    }

    public List<Template> list() {
        LambdaQueryWrapper<Template> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Template::getUpdatedTime);
        return templateMapper.selectList(wrapper);
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
    public void delete(Long id) {
        templateMapper.deleteById(id);
    }
}

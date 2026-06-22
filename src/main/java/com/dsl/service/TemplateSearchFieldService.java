package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsl.entity.TemplateSearchField;
import com.dsl.mapper.TemplateSearchFieldMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateSearchFieldService {

    private final TemplateSearchFieldMapper mapper;

    public TemplateSearchFieldService(TemplateSearchFieldMapper mapper) {
        this.mapper = mapper;
    }

    public List<TemplateSearchField> getByTemplateId(Long templateId) {
        return mapper.selectList(
            new LambdaQueryWrapper<TemplateSearchField>()
                .eq(TemplateSearchField::getTemplateId, templateId)
                .orderByAsc(TemplateSearchField::getFieldCode)
        );
    }

    @Transactional
    public void saveFields(String templateId, List<TemplateSearchField> fields) {
        Long tid = Long.valueOf(templateId);
        // 删除原有检索条件
        mapper.delete(
            new LambdaQueryWrapper<TemplateSearchField>()
                .eq(TemplateSearchField::getTemplateId, tid)
        );
        // 保存新的检索条件
        if (fields != null && !fields.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (TemplateSearchField field : fields) {
                field.setId(null);
                field.setTemplateId(tid);
                field.setCreateTime(now);
                field.setUpdateTime(now);
                mapper.insert(field);
            }
        }
    }

    @Transactional
    public void deleteByTemplateId(Long templateId) {
        mapper.delete(
            new LambdaQueryWrapper<TemplateSearchField>()
                .eq(TemplateSearchField::getTemplateId, templateId)
        );
    }
}

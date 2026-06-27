package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.mapper.TemplateNodeConstraintMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateNodeConstraintService {

    private final TemplateNodeConstraintMapper mapper;

    public TemplateNodeConstraintService(TemplateNodeConstraintMapper mapper) {
        this.mapper = mapper;
    }

    public List<TemplateNodeConstraint> getByTemplateId(Long templateId) {
        return mapper.selectList(new LambdaQueryWrapper<TemplateNodeConstraint>()
                .eq(TemplateNodeConstraint::getTemplateId, templateId)
                .orderByAsc(TemplateNodeConstraint::getNodePath));
    }

    @Transactional
    public void saveConstraints(Long templateId, List<TemplateNodeConstraint> constraints) {
        mapper.delete(new LambdaQueryWrapper<TemplateNodeConstraint>()
                .eq(TemplateNodeConstraint::getTemplateId, templateId));
        if (constraints != null) {
            for (TemplateNodeConstraint c : constraints) {
                c.setId(null);
                c.setTemplateId(templateId);
                c.setCreatedTime(LocalDateTime.now());
                c.setUpdatedTime(LocalDateTime.now());
                mapper.insert(c);
            }
        }
    }

    @Transactional
    public void deleteByTemplateId(Long templateId) {
        mapper.delete(new LambdaQueryWrapper<TemplateNodeConstraint>()
                .eq(TemplateNodeConstraint::getTemplateId, templateId));
    }
}

package com.dsl.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dsl.entity.TemplateNodeConfig;
import com.dsl.mapper.TemplateNodeConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TemplateNodeConfigService {

    private final TemplateNodeConfigMapper mapper;

    public TemplateNodeConfigService(TemplateNodeConfigMapper mapper) {
        this.mapper = mapper;
    }

    public List<TemplateNodeConfig> getByTemplateId(Long templateId) {
        return mapper.selectList(
            new LambdaQueryWrapper<TemplateNodeConfig>()
                .eq(TemplateNodeConfig::getTemplateId, templateId)
                .orderByAsc(TemplateNodeConfig::getLoopLevel)
        );
    }

    @Transactional
    public void saveConfigs(Long templateId, List<TemplateNodeConfig> configs) {
        // Delete existing configs
        mapper.delete(
            new LambdaQueryWrapper<TemplateNodeConfig>()
                .eq(TemplateNodeConfig::getTemplateId, templateId)
        );
        // Insert new configs
        if (configs != null && !configs.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (TemplateNodeConfig config : configs) {
                config.setId(null);
                config.setTemplateId(templateId);
                config.setCreatedTime(now);
                config.setUpdatedTime(now);
                mapper.insert(config);
            }
        }
    }

    @Transactional
    public void deleteByTemplateId(Long templateId) {
        mapper.delete(
            new LambdaQueryWrapper<TemplateNodeConfig>()
                .eq(TemplateNodeConfig::getTemplateId, templateId)
        );
    }
}

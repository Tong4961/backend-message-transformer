package com.dsl.service;

import com.dsl.common.exception.BizException;
import com.dsl.engine.model.TreeNode;
import com.dsl.engine.validator.MessageValidationEngine;
import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.entity.Template;
import com.dsl.entity.TemplateNodeConfig;
import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.entity.TemplateSearchField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ValidationService {

    @Autowired
    private TemplateService templateService;
    @Autowired
    private TemplateNodeConstraintService constraintService;
    @Autowired
    private TemplateNodeConfigService nodeConfigService;
    @Autowired
    private TemplateSearchFieldService searchFieldService;
    @Autowired
    private MessageValidationEngine validationEngine;
    @Autowired
    private com.dsl.engine.parser.XmlSourceParser xmlSourceParser;
    @Autowired
    private com.dsl.engine.parser.JsonSourceParser jsonSourceParser;

    public ValidationResult validate(Long templateId, String messageType, String content) {
        Template template = templateService.getById(templateId);
        if (template == null) {
            throw new BizException("模板不存在: " + templateId);
        }

        String format = template.getFormat();
        if (format == null) format = "XML";

        // 按模板格式解析结构树
        List<TreeNode> templateStructure = null;
        if (template.getSchemaData() != null && !template.getSchemaData().isEmpty()) {
            if ("JSON".equals(format)) {
                templateStructure = jsonSourceParser.parseStructure(template.getSchemaData());
            } else {
                templateStructure = xmlSourceParser.parseStructure(template.getSchemaData());
            }
        }

        List<TemplateNodeConstraint> constraints = constraintService.getByTemplateId(templateId);
        List<TemplateNodeConfig> nodeConfigs = nodeConfigService.getByTemplateId(templateId);
        List<TemplateSearchField> searchFields = searchFieldService.getByTemplateId(templateId);

        ValidationContext context = new ValidationContext();
        context.setContent(content);
        context.setFormat(format);
        context.setTemplateStructure(templateStructure);
        context.setConstraints(constraints);
        context.setNodeConfigs(nodeConfigs);
        context.setSearchFields(searchFields);

        return validationEngine.validate(context);
    }
}

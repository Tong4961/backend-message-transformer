package com.dsl.controller;

import com.dsl.common.result.R;
import com.dsl.common.util.TraceUtil;
import com.dsl.engine.MappingEngine;
import com.dsl.engine.model.MappingRuleVO;
import com.dsl.engine.model.TransformResult;
import com.dsl.engine.parser.JsonSourceParser;
import com.dsl.engine.parser.XmlSourceParser;
import com.dsl.engine.model.TreeNode;
import com.dsl.entity.AuditLog;
import com.dsl.entity.Converter;
import com.dsl.entity.MappingRule;
import com.dsl.entity.Template;
import com.dsl.service.AuditService;
import com.dsl.service.ConverterService;
import com.dsl.service.MappingRuleService;
import com.dsl.service.TemplateService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transform")
public class TransformController {

    private final MappingEngine mappingEngine;
    private final ConverterService converterService;
    private final MappingRuleService mappingRuleService;
    private final AuditService auditService;
    private final TemplateService templateService;
    private final XmlSourceParser xmlSourceParser;
    private final JsonSourceParser jsonSourceParser;

    public TransformController(MappingEngine mappingEngine, ConverterService converterService,
                              MappingRuleService mappingRuleService, AuditService auditService,
                              TemplateService templateService,
                              XmlSourceParser xmlSourceParser, JsonSourceParser jsonSourceParser) {
        this.mappingEngine = mappingEngine;
        this.converterService = converterService;
        this.mappingRuleService = mappingRuleService;
        this.auditService = auditService;
        this.templateService = templateService;
        this.xmlSourceParser = xmlSourceParser;
        this.jsonSourceParser = jsonSourceParser;
    }

    @PostMapping("/execute")
    public R<TransformResult> execute(@RequestBody TransformRequest request) {
        Converter converter = converterService.getById(request.getConverterId());
        if (converter == null) {
            return R.fail("转换器不存在");
        }

        List<MappingRule> rules = mappingRuleService.getByConverterId(request.getConverterId());
        List<MappingRuleVO> ruleVOs = rules.stream().map(this::toVO).collect(Collectors.toList());

        // Get target schema from converter or its target template
        String targetSchema = converter.getTargetSample();
        if ((targetSchema == null || targetSchema.isEmpty()) && converter.getTargetTemplateId() != null) {
            Template tpl = templateService.getById(converter.getTargetTemplateId());
            if (tpl != null) targetSchema = tpl.getSchemaData();
        }

        TransformResult result = mappingEngine.transform(
            request.getInputData(),
            converter.getSourceFormat(),
            converter.getTargetFormat(),
            ruleVOs,
            request.getRootName(),
            targetSchema
        );

        // Save audit log
        AuditLog auditLog = new AuditLog();
        auditLog.setTraceId(result.getTraceId());
        auditLog.setConverterId(converter.getId());
        auditLog.setConverterCode(converter.getCode());
        auditLog.setConverterVersion(converter.getVersion());
        auditLog.setInputData(request.getInputData());
        auditLog.setOutputData(result.getOutputData());
        auditLog.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        auditLog.setErrorMessage(result.getErrorMessage());
        auditLog.setDuration(result.getDuration());
        auditLog.setRequestTime(LocalDateTime.now());
        auditService.save(auditLog);

        return R.ok(result);
    }

    @PostMapping("/test")
    public R<TransformResult> test(@RequestBody TestRequest request) {
        Converter converter = converterService.getById(request.getConverterId());
        if (converter == null) {
            return R.fail("转换器不存在");
        }

        List<MappingRule> rules = mappingRuleService.getByConverterId(request.getConverterId());
        List<MappingRuleVO> ruleVOs = rules.stream().map(this::toVO).collect(Collectors.toList());

        // Get target schema from converter or its target template
        String targetSchema = converter.getTargetSample();
        if ((targetSchema == null || targetSchema.isEmpty()) && converter.getTargetTemplateId() != null) {
            Template tpl = templateService.getById(converter.getTargetTemplateId());
            if (tpl != null) targetSchema = tpl.getSchemaData();
        }

        TransformResult result = mappingEngine.transform(
            request.getInputData(),
            converter.getSourceFormat(),
            converter.getTargetFormat(),
            ruleVOs,
            "root",
            targetSchema
        );
        return R.ok(result);
    }

    @PostMapping("/parse-structure")
    public R<List<TreeNode>> parseStructure(@RequestBody ParseRequest request) {
        List<TreeNode> nodes;
        if ("XML".equalsIgnoreCase(request.getFormat()) || "HL7_V3".equalsIgnoreCase(request.getFormat())) {
            nodes = xmlSourceParser.parseStructure(request.getData());
        } else {
            nodes = jsonSourceParser.parseStructure(request.getData());
        }
        return R.ok(nodes);
    }

    private MappingRuleVO toVO(MappingRule rule) {
        MappingRuleVO vo = new MappingRuleVO();
        vo.setId(rule.getId());
        vo.setSourceExpression(rule.getSourceExpression());
        vo.setTargetExpression(rule.getTargetExpression());
        vo.setSourceType(rule.getSourceType());
        vo.setTargetType(rule.getTargetType());
        vo.setConverterChain(rule.getConverterChain());
        vo.setDefaultValue(rule.getDefaultValue());
        vo.setNullPolicy(rule.getNullPolicy());
        vo.setConditionExpression(rule.getConditionExpression());
        vo.setConditionValue(rule.getConditionValue());
        return vo;
    }

    public static class TransformRequest {
        private Long converterId;
        private String inputData;
        private String rootName;

        public Long getConverterId() { return converterId; }
        public void setConverterId(Long converterId) { this.converterId = converterId; }
        public String getInputData() { return inputData; }
        public void setInputData(String inputData) { this.inputData = inputData; }
        public String getRootName() { return rootName; }
        public void setRootName(String rootName) { this.rootName = rootName; }
    }

    public static class TestRequest {
        private Long converterId;
        private String inputData;

        public Long getConverterId() { return converterId; }
        public void setConverterId(Long converterId) { this.converterId = converterId; }
        public String getInputData() { return inputData; }
        public void setInputData(String inputData) { this.inputData = inputData; }
    }

    public static class ParseRequest {
        private String data;
        private String format;

        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        public String getFormat() { return format; }
        public void setFormat(String format) { this.format = format; }
    }
}

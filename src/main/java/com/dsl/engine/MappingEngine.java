package com.dsl.engine;

import org.apache.commons.lang3.StringUtils;
import com.dsl.common.enums.NullPolicy;
import com.dsl.engine.builder.JsonTargetBuilder;
import com.dsl.engine.builder.TargetBuilder;
import com.dsl.engine.builder.XmlTargetBuilder;
import com.dsl.engine.converter.ConverterFactory;
import com.dsl.engine.model.MappingRuleVO;
import com.dsl.engine.model.TransformContext;
import com.dsl.engine.model.TransformResult;
import com.dsl.engine.parser.JsonSourceParser;
import com.dsl.engine.parser.SourceParser;
import com.dsl.engine.parser.XmlSourceParser;
import com.dsl.plugin.PluginFunction;
import com.dsl.plugin.PluginRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;

@Component
public class MappingEngine {

    private static final Logger log = LoggerFactory.getLogger(MappingEngine.class);

    private final XmlSourceParser xmlSourceParser;
    private final JsonSourceParser jsonSourceParser;
    private final XmlTargetBuilder xmlTargetBuilder;
    private final JsonTargetBuilder jsonTargetBuilder;
    private final ConverterFactory converterFactory;
    private final ExpressionEvaluator expressionEvaluator;
    private final PluginRegistry pluginRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MappingEngine(XmlSourceParser xmlSourceParser, JsonSourceParser jsonSourceParser,
                         XmlTargetBuilder xmlTargetBuilder, JsonTargetBuilder jsonTargetBuilder,
                         ConverterFactory converterFactory, ExpressionEvaluator expressionEvaluator,
                         PluginRegistry pluginRegistry) {
        this.xmlSourceParser = xmlSourceParser;
        this.jsonSourceParser = jsonSourceParser;
        this.xmlTargetBuilder = xmlTargetBuilder;
        this.jsonTargetBuilder = jsonTargetBuilder;
        this.converterFactory = converterFactory;
        this.expressionEvaluator = expressionEvaluator;
        this.pluginRegistry = pluginRegistry;
    }

    public TransformResult transform(String sourceData, String sourceFormat, String targetFormat,
                                     List<MappingRuleVO> rules, String rootName, String targetSample) {
        TransformContext context = new TransformContext();
        context.setSourceData(sourceData);
        context.setSourceFormat(sourceFormat);
        context.setTargetFormat(targetFormat);
        context.setTraceId(UUID.randomUUID().toString().replace("-", ""));

        long startTime = System.currentTimeMillis();
        TransformResult result = new TransformResult();
        result.setTraceId(context.getTraceId());

        try {
            SourceParser parser = getSourceParser(sourceFormat);
            TargetBuilder builder = getTargetBuilder(targetFormat);

            builder.setRoot(rootName != null ? rootName : "root");

            // Initialize default structure from target sample/template
            if (targetSample != null && !targetSample.isEmpty()) {
                builder.initDefaults(targetSample, targetFormat);
                context.addLog("已加载目标模板，长度: " + targetSample.length());
            } else {
                context.addLog("未提供目标模板，将从零构建");
            }

            Map<String, Object> variables = new HashMap<>();

            for (MappingRuleVO rule : rules) {
                try {
                    processRule(parser, builder, sourceData, rule, variables, context);
                } catch (Exception e) {
                    context.addLog("规则执行异常 [" + rule.getSourceExpression() + "]: " + e.getMessage());
                    if ("ERROR".equals(rule.getNullPolicy())) {
                        throw e;
                    }
                }
            }

            result.setSuccess(true);
            result.setOutputData(builder.build());
            result.setDuration(System.currentTimeMillis() - startTime);
            result.setLogs(context.getLogs());
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            result.setDuration(System.currentTimeMillis() - startTime);
            result.setLogs(context.getLogs());
        }

        return result;
    }

    private void processRule(SourceParser parser, TargetBuilder builder,
                             String sourceData, MappingRuleVO rule,
                             Map<String, Object> variables, TransformContext context) {

        // Skip rules with empty source or target expression
        if (rule.getSourceExpression() == null || rule.getSourceExpression().trim().isEmpty()) {
            context.addLog("跳过空规则: sourceExpression为空");
            return;
        }
        if (rule.getTargetExpression() == null || rule.getTargetExpression().trim().isEmpty()) {
            context.addLog("跳过空规则: targetExpression为空, source=" + rule.getSourceExpression());
            return;
        }

        // Extract source value first so it's available in conditions
        Object value = parser.extractValue(sourceData, rule.getSourceExpression());
        context.addLog("提取 [" + rule.getSourceExpression() + "] = " + value);

        // Store source value as variable for condition evaluation
        if (rule.getSourceExpression() != null && !rule.getSourceExpression().isEmpty()) {
            String srcExpr = rule.getSourceExpression();
            // 1. Always store by full path (unique, no collision)
            variables.put(srcExpr, value);

            // 2. Parse segments: /patient/gender/@code -> ["patient","gender","code"]
            String[] rawParts = srcExpr.split("/");
            java.util.List<String> parts = new java.util.ArrayList<>();
            for (String p : rawParts) {
                String cleaned = p.replace("@", "").replace("$", "").replace(".", "").trim();
                if (!cleaned.isEmpty()) parts.add(cleaned);
            }

            if (parts.size() >= 2) {
                String last = parts.get(parts.size() - 1);
                String parent = parts.get(parts.size() - 2);
                String specificKey = parent + "_" + last; // e.g. gender_code, id_code

                // 3. Store parent_last (almost always unique)
                variables.put(specificKey, value);

                // 4. For simple last-segment name: only store if no collision
                // If another path already mapped to this name with a different key, mark ambiguous
                String simpleKey = last; // e.g. code
                if (variables.containsKey("__ambig_" + simpleKey)) {
                    // Already ambiguous, remove the simple name
                    variables.remove(simpleKey);
                } else if (variables.containsKey(simpleKey)) {
                    // Check if it came from a different source path
                    Object existing = variables.get(simpleKey);
                    if (!java.util.Objects.equals(existing, value)) {
                        // Collision detected: mark ambiguous, remove simple name
                        variables.put("__ambig_" + simpleKey, Boolean.TRUE);
                        variables.remove(simpleKey);
                    }
                } else {
                    // First time, store it
                    variables.put(simpleKey, value);
                }
            } else if (parts.size() == 1) {
                variables.put(parts.get(0), value);
            }
        }

        // Evaluate condition (now source value is in variables)
        boolean conditionMet = true;
        if (rule.getConditionExpression() != null && !rule.getConditionExpression().isEmpty()) {
            conditionMet = expressionEvaluator.evaluate(rule.getConditionExpression(), variables);
            if (!conditionMet) {
                context.addLog("条件不满足，跳过: " + rule.getConditionExpression());
                return;
            }
        }

        // If condition met and conditionValue is set, use it directly (skip all other processing)
        if (conditionMet && rule.getConditionValue() != null && !rule.getConditionValue().isEmpty()) {
            context.addLog("条件满足，使用条件值: " + rule.getConditionValue());
            builder.setValue(rule.getTargetExpression(), rule.getConditionValue());
            if (rule.getTargetExpression() != null) {
                String varName = rule.getTargetExpression().replaceAll("[^a-zA-Z0-9_]", "_");
                variables.put(varName, rule.getConditionValue());
                variables.put(rule.getTargetExpression(), rule.getConditionValue());
            }
            return;
        }

        // Handle null/empty/blank
        if (value == null || (value instanceof String && StringUtils.isBlank((String) value))) {
            NullPolicy policy = NullPolicy.SKIP;
            if (rule.getNullPolicy() != null) {
                try {
                    policy = NullPolicy.valueOf(rule.getNullPolicy());
                } catch (IllegalArgumentException ignored) {}
            }

            switch (policy) {
                case SKIP:
                    context.addLog("源值为空，策略SKIP，跳过: " + rule.getSourceExpression());
                    return;
                case NULL:
                    value = null;
                    break;
                case EMPTY:
                    value = "";
                    break;
                case DEFAULT:
                    value = rule.getDefaultValue();
                    break;
                case ERROR:
                    throw new RuntimeException("源字段为空: " + rule.getSourceExpression());
            }
        }

        // Apply default value if still null
        if (value == null && rule.getDefaultValue() != null) {
            value = rule.getDefaultValue();
        }

        // Apply converter chain
        if (rule.getConverterChain() != null && !rule.getConverterChain().isEmpty()) {
            // Check if it's a plugin call
            if (rule.getConverterChain().startsWith("plugin:")) {
                String pluginExpr = rule.getConverterChain().substring(7);
                String pluginCode = pluginExpr;
                String[] params = new String[0];

                // Parse params: MASK(phone) or AES(1234567890123456)
                int parenIdx = pluginExpr.indexOf('(');
                if (parenIdx > 0 && pluginExpr.endsWith(")")) {
                    pluginCode = pluginExpr.substring(0, parenIdx);
                    String paramStr = pluginExpr.substring(parenIdx + 1, pluginExpr.length() - 1);
                    if (!paramStr.isEmpty()) {
                        params = paramStr.split(",");
                        for (int i = 0; i < params.length; i++) {
                            params[i] = params[i].trim();
                        }
                    }
                }

                PluginFunction plugin = pluginRegistry.getPlugin(pluginCode);
                if (plugin != null) {
                    value = plugin.execute(value, params);
                }
            } else {
                value = converterFactory.executeChain(value, rule.getConverterChain());
            }
            context.addLog("转换 [" + rule.getConverterChain() + "] => " + value);
        }

        // Store in variables for condition evaluation
        if (rule.getTargetExpression() != null) {
            String varName = rule.getTargetExpression().replaceAll("[^a-zA-Z0-9_]", "_");
            variables.put(varName, value);
            variables.put(rule.getTargetExpression(), value);
        }

        // Set target value
        builder.setValue(rule.getTargetExpression(), value);
        context.addLog("写入 [" + rule.getTargetExpression() + "] = " + value);
    }

    private SourceParser getSourceParser(String format) {
        if ("XML".equalsIgnoreCase(format) || "HL7_V3".equalsIgnoreCase(format)) return xmlSourceParser;
        if ("JSON".equalsIgnoreCase(format)) return jsonSourceParser;
        throw new RuntimeException("不支持的源格式: " + format);
    }

    private TargetBuilder getTargetBuilder(String format) {
        if ("XML".equalsIgnoreCase(format) || "HL7_V3".equalsIgnoreCase(format)) return xmlTargetBuilder;
        if ("JSON".equalsIgnoreCase(format)) return jsonTargetBuilder;
        throw new RuntimeException("不支持的目标格式: " + format);
    }
}

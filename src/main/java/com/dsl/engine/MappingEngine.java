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
import com.fasterxml.jackson.core.type.TypeReference;
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
            Set<Long> loopProcessedRuleIds = new HashSet<>();
            // Collect LOOP target paths for child rule detection
            java.util.List<String[]> loopPaths = new java.util.ArrayList<>();
            for (MappingRuleVO r : rules) {
                if ("LOOP".equals(r.getMappingType()) && r.getTargetExpression() != null) {
                    loopPaths.add(new String[]{r.getSourceExpression(), r.getTargetExpression()});
                }
            }

            for (MappingRuleVO rule : rules) {
                // Skip rules already processed as part of a loop
                if (loopProcessedRuleIds.contains(rule.getId())) {
                    continue;
                }
                // Skip child rules belonging to a LOOP (source AND target strictly under array paths)
                if (!"LOOP".equals(rule.getMappingType())) {
                    boolean skip = false;
                    for (String[] lp : loopPaths) {
                        boolean srcUnder = isStrictlyUnderArray(rule.getSourceExpression(), lp[0]);
                        boolean tgtUnder = isStrictlyUnderArray(rule.getTargetExpression(), lp[1]);
                        if (srcUnder && tgtUnder) {
                            skip = true;
                            break;
                        }
                    }
                    if (skip) continue;
                }
                try {
                    // If target contains [*] and not already LOOP, treat as implicit loop
                    if (!"LOOP".equals(rule.getMappingType()) && rule.getTargetExpression() != null
                            && rule.getTargetExpression().contains("[*]")) {
                        processImplicitLoop(parser, builder, sourceData, rule, variables, context, rules, loopProcessedRuleIds);
                    } else {
                        processRule(parser, builder, sourceData, rule, variables, context, rules, loopProcessedRuleIds);
                    }
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
                             Map<String, Object> variables, TransformContext context,
                             List<MappingRuleVO> allRules, Set<Long> loopProcessedRuleIds) {

        // Skip rules with empty source or target expression
        if (rule.getSourceExpression() == null || rule.getSourceExpression().trim().isEmpty()) {
            context.addLog("跳过空规则: sourceExpression为空");
            return;
        }
        if (rule.getTargetExpression() == null || rule.getTargetExpression().trim().isEmpty()) {
            context.addLog("跳过空规则: targetExpression为空, source=" + rule.getSourceExpression());
            return;
        }

        // Handle LOOP mapping type
        if ("LOOP".equals(rule.getMappingType())) {
            processLoopRule(parser, builder, sourceData, rule, variables, context, allRules, loopProcessedRuleIds);
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

    /**
     * 处理 LOOP 类型的映射规则：遍历源数组，为每个元素应用子规则映射到目标数组
     */
    private void processLoopRule(SourceParser parser, TargetBuilder builder,
                                 String sourceData, MappingRuleVO rule,
                                 Map<String, Object> variables, TransformContext context,
                                 List<MappingRuleVO> allRules, Set<Long> loopProcessedRuleIds) {

        String sourceArrayPath = rule.getSourceExpression();
        String targetArrayPath = rule.getTargetExpression();

        context.addLog("LOOP 开始: 源=" + sourceArrayPath + " → 目标=" + targetArrayPath);

        // Extract source array node contexts (actual Node/JsonNode objects)
        List<Object> nodeContexts = parser.extractNodeContexts(sourceData, sourceArrayPath);
        if (nodeContexts.isEmpty()) {
            context.addLog("LOOP: 源数组为空，跳过");
            loopProcessedRuleIds.add(rule.getId());
            return;
        }

        // Find child rules: source under sourceArrayPath AND target under targetArrayPath
        List<MappingRuleVO> childRules = new ArrayList<>();
        for (MappingRuleVO r : allRules) {
            if (r.getId().equals(rule.getId())) continue;
            if ("LOOP".equals(r.getMappingType())) continue;
            boolean srcUnder = r.getSourceExpression() != null && isChildOfArray(r.getSourceExpression(), sourceArrayPath);
            boolean tgtUnder = r.getTargetExpression() != null && isChildOfArray(r.getTargetExpression(), targetArrayPath);
            if (srcUnder && tgtUnder) {
                childRules.add(r);
            }
        }

        if (childRules.isEmpty()) {
            context.addLog("LOOP: 未找到子规则，跳过");
            loopProcessedRuleIds.add(rule.getId());
            return;
        }
        context.addLog("LOOP: 找到 " + childRules.size() + " 个子规则");

        // Detect parent array paths for nested loop support
        // 优先通过 parentLoopCode 查找父循环，回退到路径推断
        String parentArrayPath = findParentArrayPathByLoopCode(rule, allRules);
        if (parentArrayPath == null) {
            parentArrayPath = findParentArrayPath(targetArrayPath);
        }

        // Separate child rules: direct parent fields vs nested array fields
        List<MappingRuleVO> parentDirectRules = new ArrayList<>();
        List<MappingRuleVO> nestedArrayRules = new ArrayList<>();
        List<MappingRuleVO> normalChildRules = new ArrayList<>();
        for (MappingRuleVO cr : childRules) {
            String tgt = cr.getTargetExpression();
            context.addLog("  分类规则: " + cr.getSourceExpression() + " → " + tgt);
            if (parentArrayPath != null && tgt.equals(parentArrayPath)) {
                parentDirectRules.add(cr);
                context.addLog("    → 父字段 (tgt.equals(parentArrayPath))");
            } else if (parentArrayPath != null && isChildOfArray(tgt, parentArrayPath) && !tgt.equals(targetArrayPath) && hasArrayInPath(tgt, targetArrayPath)) {
                nestedArrayRules.add(cr);
                context.addLog("    → 嵌套 (isChildOfArray=" + isChildOfArray(tgt, parentArrayPath) + ", hasArrayInPath=" + hasArrayInPath(tgt, targetArrayPath) + ")");
            } else {
                normalChildRules.add(cr);
                context.addLog("    → 普通");
            }
        }
        context.addLog("LOOP: 规则分类 - 父字段:" + parentDirectRules.size() + " 嵌套:" + nestedArrayRules.size() + " 普通:" + normalChildRules.size());

        // Group source contexts by parent element for nested arrays
        // e.g. /patient/provider/telecom → group telecoms by their parent <provider>
        java.util.Map<Object, java.util.List<Object>> parentGroups = new java.util.LinkedHashMap<>();
        if (parentArrayPath != null) {
            for (Object ctx : nodeContexts) {
                Object parent = parser.getParentContext(ctx);
                if (parent == null) parent = ctx; // fallback
                parentGroups.computeIfAbsent(parent, k -> new java.util.ArrayList<>()).add(ctx);
            }
        }

        if (parentArrayPath != null && !parentGroups.isEmpty()) {
            // Nested mode: iterate parent groups
            int groupIdx = 0;
            for (java.util.Map.Entry<Object, java.util.List<Object>> entry : parentGroups.entrySet()) {
                Object parentCtx = entry.getKey();
                java.util.List<Object> children = entry.getValue();
                groupIdx++;

                // Check if parent array item already exists (from implicit loop)
                java.util.List<Object> existingParents = builder.getArrayItems(parentArrayPath);
                Object parentItem;
                if (existingParents != null && existingParents.size() >= groupIdx) {
                    parentItem = existingParents.get(groupIdx - 1);
                    context.addLog("LOOP: 复用父数组项 #" + groupIdx);
                } else {
                    parentItem = builder.addArrayItem(parentArrayPath);
                    context.addLog("LOOP: 创建父数组项 #" + groupIdx);
                }

                // Set direct fields on parent (e.g. id, name from provider)
                for (MappingRuleVO pr : parentDirectRules) {
                    Object pv = extractChildValue(parser, sourceData, parentCtx,
                            sourceArrayPath.replaceAll("/[^/]+$", ""), pr);
                    if (pv != null && pr.getConverterChain() != null && !pr.getConverterChain().isEmpty()) {
                        pv = converterFactory.executeChain(pv, pr.getConverterChain());
                    }
                    if (pv == null && pr.getDefaultValue() != null) pv = pr.getDefaultValue();
                    if (pv != null && parentItem != null) {
                        String relTgt = stripArrayPrefix(pr.getTargetExpression(), parentArrayPath);
                        builder.setValueInArrayItem(parentItem, relTgt, pv);
                        context.addLog("  父字段 [" + relTgt + "] = " + pv);
                    }
                }

                // Add child array items (e.g. contacts from telecoms)
                // First, create the child array on the parent item if it doesn't exist
                String childArrayRelativePath = stripArrayPrefix(targetArrayPath, parentArrayPath);
                int childIdx = 0;
                for (Object childCtx : children) {
                    childIdx++;
                    context.addLog("LOOP: 处理子元素 #" + groupIdx + "-" + childIdx);

                    // Create a new item in the child array under the parent item
                    Object arrayItem = builder.addArrayItemInParent(parentItem, childArrayRelativePath);

                    for (MappingRuleVO childRule : normalChildRules) {
                        if (childRule.getTargetExpression() == null || childRule.getTargetExpression().trim().isEmpty()) continue;
                        String relativeSource = stripArrayPrefix(childRule.getSourceExpression(), sourceArrayPath);
                        String relativeTarget = stripArrayPrefix(childRule.getTargetExpression(), targetArrayPath);

                        Object value;
                        String srcNorm = childRule.getSourceExpression() != null ?
                                childRule.getSourceExpression().replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "") : "";
                        String arrNorm = sourceArrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
                        if (srcNorm.equals(arrNorm)) {
                            value = parser.extractValueFromContext(childCtx, ".");
                        } else if (isChildOfArray(childRule.getSourceExpression(), sourceArrayPath)) {
                            value = parser.extractValueFromContext(childCtx, relativeSource);
                        } else {
                            value = parser.extractValue(sourceData, childRule.getSourceExpression());
                        }
                        context.addLog("  子规则提取 [" + relativeSource + "] = " + value);

                        if (value != null && childRule.getConverterChain() != null && !childRule.getConverterChain().isEmpty()) {
                            value = converterFactory.executeChain(value, childRule.getConverterChain());
                        }
                        if (value == null && childRule.getDefaultValue() != null) value = childRule.getDefaultValue();

                        if (value != null) {
                            builder.setValueInArrayItem(arrayItem, relativeTarget, value);
                            context.addLog("  子规则写入 [" + relativeTarget + "] = " + value);
                        }
                    }
                }
            }
        } else {
            // Flat mode: no parent grouping needed
            for (int i = 0; i < nodeContexts.size(); i++) {
                Object itemContext = nodeContexts.get(i);
                Object arrayItem = builder.addArrayItem(targetArrayPath);
                context.addLog("LOOP: 处理第 " + (i + 1) + " 个元素");

                for (MappingRuleVO childRule : normalChildRules) {
                    if (childRule.getTargetExpression() == null || childRule.getTargetExpression().trim().isEmpty()) continue;
                    String relativeSource = stripArrayPrefix(childRule.getSourceExpression(), sourceArrayPath);
                    String relativeTarget = stripArrayPrefix(childRule.getTargetExpression(), targetArrayPath);

                    Object value;
                    String srcNorm = childRule.getSourceExpression() != null ?
                            childRule.getSourceExpression().replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "") : "";
                    String arrNorm = sourceArrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
                    if (srcNorm.equals(arrNorm)) {
                        value = parser.extractValueFromContext(itemContext, ".");
                    } else if (isChildOfArray(childRule.getSourceExpression(), sourceArrayPath)) {
                        value = parser.extractValueFromContext(itemContext, relativeSource);
                    } else {
                        value = parser.extractValue(sourceData, childRule.getSourceExpression());
                    }
                    context.addLog("  子规则提取 [" + relativeSource + "] = " + value);

                    if (value != null && childRule.getConverterChain() != null && !childRule.getConverterChain().isEmpty()) {
                        value = converterFactory.executeChain(value, childRule.getConverterChain());
                    }
                    if (value == null && childRule.getDefaultValue() != null) value = childRule.getDefaultValue();

                    if (value != null) {
                        builder.setValueInArrayItem(arrayItem, relativeTarget, value);
                        context.addLog("  子规则写入 [" + relativeTarget + "] = " + value);
                    }
                }
            }
        }

        loopProcessedRuleIds.add(rule.getId());
        context.addLog("LOOP 完成: 共处理 " + nodeContexts.size() + " 个元素");
    }

    /**
     * 处理隐式循环：规则目标含[*]但未标记为LOOP类型
     * e.g. /patient/provider/id → $.providers[*].id (DIRECT规则自动按循环处理)
     *
     * 将目标路径拆分为：数组路径 + 相对字段路径
     * e.g. $.providers[*].id → 数组=$.providers[*], 字段=id
     */
    private void processImplicitLoop(SourceParser parser, TargetBuilder builder,
                                      String sourceData, MappingRuleVO rule,
                                      Map<String, Object> variables, TransformContext context,
                                      List<MappingRuleVO> allRules, Set<Long> loopProcessedRuleIds) {
        String sourcePath = rule.getSourceExpression();
        String targetPath = rule.getTargetExpression();
        context.addLog("隐式LOOP: " + sourcePath + " → " + targetPath);

        List<Object> nodeContexts = parser.extractNodeContexts(sourceData, sourcePath);
        if (nodeContexts.isEmpty()) {
            context.addLog("隐式LOOP: 源数组为空，跳过");
            loopProcessedRuleIds.add(rule.getId());
            return;
        }

        // Split target into array path and relative field path
        // $.providers[*].id → arrayPath=$.providers[*], fieldPath=id
        int lastBracket = targetPath.lastIndexOf("[*]");
        String arrayPath, fieldPath;
        if (lastBracket >= 0 && lastBracket + 3 < targetPath.length()) {
            arrayPath = targetPath.substring(0, lastBracket + 3);
            fieldPath = targetPath.substring(lastBracket + 3);
            if (fieldPath.startsWith(".")) fieldPath = fieldPath.substring(1);
        } else {
            arrayPath = targetPath;
            fieldPath = "";
        }

        // Use parent contexts (the array elements) instead of leaf contexts
        // 直接取 NodeWithParent.getParent() 获取实际父节点用于值提取，
        // 不能用 getParentContext()（它优先返回 parentId 字符串，仅用于分组）
        String srcNorm = sourcePath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String[] srcParts = srcNorm.split("/");
        String srcField = srcParts.length > 0 ? srcParts[srcParts.length - 1] : "";

        java.util.List<Object> parentContexts = new java.util.ArrayList<>();
        for (Object ctx : nodeContexts) {
            if (ctx instanceof com.dsl.engine.model.NodeWithParent) {
                Object p = ((com.dsl.engine.model.NodeWithParent) ctx).getParent();
                parentContexts.add(p != null ? p : ctx);
            } else {
                parentContexts.add(ctx);
            }
        }

        // Check if target array already has items
        java.util.List<Object> existingItems = builder.getArrayItems(arrayPath);
        int count = Math.max(parentContexts.size(), existingItems != null ? existingItems.size() : 0);

        for (int i = 0; i < count; i++) {
            Object itemContext = i < parentContexts.size() ? parentContexts.get(i) : null;
            Object arrayItem;

            if (existingItems != null && i < existingItems.size()) {
                arrayItem = existingItems.get(i);
                context.addLog("隐式LOOP: 复用数组项 #" + (i + 1));
            } else {
                arrayItem = builder.addArrayItem(arrayPath);
                context.addLog("隐式LOOP: 创建数组项 #" + (i + 1));
            }

            if (itemContext != null && !fieldPath.isEmpty()) {
                Object value = parser.extractValueFromContext(itemContext, srcField);
                context.addLog("  隐式提取 [" + srcField + "] = " + value);

                if (value != null && rule.getConverterChain() != null && !rule.getConverterChain().isEmpty()) {
                    value = converterFactory.executeChain(value, rule.getConverterChain());
                }
                if (value == null && rule.getDefaultValue() != null) {
                    value = rule.getDefaultValue();
                }
                if (value != null) {
                    builder.setValueInArrayItem(arrayItem, fieldPath, value);
                    context.addLog("  隐式写入 [" + fieldPath + "] = " + value);
                }
            }
        }

        loopProcessedRuleIds.add(rule.getId());
        context.addLog("隐式LOOP 完成: 共处理 " + nodeContexts.size() + " 个元素");
    }

    /**
     * 严格判断子路径是否在数组路径下（排除相等的情况）
     */
    private boolean isStrictlyUnderArray(String childPath, String arrayPath) {
        if (childPath == null || arrayPath == null) return false;
        String normalizedArray = arrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String normalizedChild = childPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        return normalizedChild.startsWith(normalizedArray) && !normalizedChild.equals(normalizedArray);
    }

    /**
     * 提取子规则的值，处理源路径等于当前数组的情况
     */
    private Object extractChildValue(SourceParser parser, String sourceData, Object itemContext,
                                      String sourceArrayPath, MappingRuleVO childRule) {
        String src = childRule.getSourceExpression();
        if (src == null) return null;
        // If source is the array itself, extract from current item context
        String normalized = src.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String normalizedArray = sourceArrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        if (normalized.equals(normalizedArray)) {
            return parser.extractValueFromContext(itemContext, ".");
        }
        if (isChildOfArray(src, sourceArrayPath)) {
            String rel = stripArrayPrefix(src, sourceArrayPath);
            return parser.extractValueFromContext(itemContext, rel);
        }
        return parser.extractValue(sourceData, src);
    }

    /**
     * 查找目标路径中的父数组路径
     * e.g. $.providers[*].contacts → $.providers[*]
     */
    private String findParentArrayPath(String targetPath) {
        if (targetPath == null) return null;
        // Find the first [*] and check if there's more path after it
        int idx = targetPath.indexOf("[*]");
        if (idx < 0) return null;
        String after = targetPath.substring(idx + 3);
        if (after.isEmpty() || after.matches("[./].*")) {
            // There's more path after [*], so this is a parent array
            return targetPath.substring(0, idx + 3);
        }
        return null;
    }

    /**
     * 检查路径在去掉父数组前缀后是否还包含数组符号
     */
    private boolean hasArrayInPath(String path, String parentPath) {
        String stripped = path;
        if (stripped.startsWith(parentPath)) {
            stripped = stripped.substring(parentPath.length());
        }
        // 去除前导的 [*] 或 [0]（属于当前子数组自身，不是更深层嵌套）
        stripped = stripped.replaceFirst("^(\\[\\*\\]|\\[0\\])", "");
        // 再去除前导的 . 或 /
        while (stripped.startsWith(".") || stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped.contains("[*]") || stripped.contains("[0]");
    }

    /**
     * 判断子规则的源路径是否属于数组路径下
     */
    private boolean isChildOfArray(String childPath, String arrayPath) {
        if (childPath == null || arrayPath == null) return false;
        // JSON: $.telecom[*].value starts with $.telecom
        String normalizedArray = arrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String normalizedChild = childPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");

        // Direct prefix match
        if (normalizedChild.startsWith(normalizedArray) && !normalizedChild.equals(normalizedArray)) {
            return true;
        }

        // Relative path match: childPath is relative to arrayPath's last segment
        // e.g. "telecom/value" is child of "/patient/provider/telecom"
        String[] arrayParts = normalizedArray.split("[./]");
        String lastSegment = arrayParts[arrayParts.length - 1];
        if (normalizedChild.startsWith(lastSegment + "/") || normalizedChild.equals(lastSegment)) {
            return true;
        }

        return false;
    }

    /**
     * 去掉子路径中的数组前缀，返回相对路径
     */
    private String stripArrayPrefix(String childPath, String arrayPath) {
        if (childPath == null) return "";
        String normalizedArray = arrayPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        // 同时归一化子路径，确保中间的 [*] 不影响前缀匹配
        String normalizedChild = childPath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String result = normalizedChild;

        // Direct prefix strip
        if (result.startsWith(normalizedArray) && !result.equals(normalizedArray)) {
            result = result.substring(normalizedArray.length());
        } else if (result.equals(normalizedArray)) {
            result = "";
        } else {
            // Relative path strip: remove arrayPath's last segment prefix
            String[] arrayParts = normalizedArray.split("[./]");
            String lastSegment = arrayParts[arrayParts.length - 1];
            // Support both / and . as separator
            if (result.startsWith(lastSegment + "/") || result.startsWith(lastSegment + ".")) {
                result = result.substring(lastSegment.length() + 1);
            } else if (result.equals(lastSegment)) {
                result = "";
            }
        }

        // Remove leading . or /
        while (result.startsWith(".") || result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
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

    // ============ loopConfig 解析方法 ============

    private static final com.fasterxml.jackson.databind.ObjectMapper loopConfigMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private String parseLoopCode(String loopConfig) {
        if (loopConfig == null || loopConfig.isEmpty()) return null;
        try {
            java.util.Map<String, Object> map = loopConfigMapper.readValue(loopConfig,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            return (String) map.get("loopCode");
        } catch (Exception e) { return null; }
    }

    private String parseParentLoopCode(String loopConfig) {
        if (loopConfig == null || loopConfig.isEmpty()) return null;
        try {
            java.util.Map<String, Object> map = loopConfigMapper.readValue(loopConfig,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() {});
            return (String) map.get("parentLoopCode");
        } catch (Exception e) { return null; }
    }

    /**
     * 通过 parentLoopCode 查找父循环的目标路径
     */
    private String findParentArrayPathByLoopCode(MappingRuleVO currentRule, List<MappingRuleVO> allRules) {
        String parentLoopCode = parseParentLoopCode(currentRule.getLoopConfig());
        if (parentLoopCode == null || parentLoopCode.isEmpty()) return null;
        for (MappingRuleVO r : allRules) {
            if ("LOOP".equals(r.getMappingType()) && !r.getId().equals(currentRule.getId())) {
                String rLoopCode = parseLoopCode(r.getLoopConfig());
                if (parentLoopCode.equals(rLoopCode)) {
                    return r.getTargetExpression();
                }
            }
        }
        return null;
    }
}

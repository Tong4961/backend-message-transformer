package com.dsl.engine.validator.impl;

import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class NodeConstraintValidator implements Validator {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        List<TemplateNodeConstraint> constraints = context.getConstraints();
        if (constraints == null || constraints.isEmpty()) return;

        for (TemplateNodeConstraint constraint : constraints) {
            String nodePath = constraint.getNodePath();
            if (nodePath == null || nodePath.isEmpty()) continue;

            List<String> actualValues;
            if (context.isJson()) {
                actualValues = extractJsonValues(context.getJsonRoot(), nodePath);
            } else {
                actualValues = extractXmlValues(context.getDocument(), nodePath);
            }

            boolean hasAnyValue = false;
            for (String v : actualValues) {
                if (v != null && !v.trim().isEmpty()) {
                    hasAnyValue = true;
                    break;
                }
            }

            int total = Math.max(actualValues.size(), 1);

            // 必填校验
            if (Boolean.TRUE.equals(constraint.getRequiredFlag())) {
                if (actualValues.isEmpty()) {
                    result.addError(nodePath, "REQUIRED", "必填字段缺失", constraint.getNodeName(), "");
                    continue;
                }
                boolean allOk = true;
                for (int i = 0; i < actualValues.size(); i++) {
                    String v = actualValues.get(i);
                    if (v == null || v.trim().isEmpty()) {
                        result.addError(instancePath(nodePath, i, actualValues.size()),
                                "REQUIRED", "必填字段缺失", constraint.getNodeName(), "");
                        allOk = false;
                    }
                }
                if (!allOk) continue;
            } else if (!hasAnyValue) {
                result.addPass();
                continue;
            }

            String dataType = constraint.getDataType();
            if (dataType == null || dataType.isEmpty()) {
                result.addPasses(total);
                continue;
            }

            for (int i = 0; i < actualValues.size(); i++) {
                String actualValue = actualValues.get(i);
                if (actualValue == null || actualValue.trim().isEmpty()) {
                    result.addPass();
                    continue;
                }
                String ipath = instancePath(nodePath, i, actualValues.size());
                boolean valid = validateValue(constraint, actualValue, dataType, ipath, result);
                if (valid) result.addPass();
            }
        }
    }

    private String instancePath(String basePath, int index, int total) {
        return total > 1 ? basePath + "[" + (index + 1) + "]" : basePath;
    }

    // ============ XML 多值提取 ============

    private List<String> extractXmlValues(Document doc, String nodePath) {
        List<String> values = new ArrayList<>();
        if (doc == null) return values;
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            // 剥离 [*] 和 [idx] 使 XPath 匹配所有同名节点
            String xpathExpr = nodePath.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
            NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String value;
                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    value = node.getNodeValue();
                } else {
                    value = node.getTextContent();
                }
                values.add(value != null ? value : "");
            }
        } catch (Exception e) {
            // 回退: 尝试单值提取
            String single = extractXmlSingle(doc, nodePath);
            if (single != null) values.add(single);
        }
        return values;
    }

    private String extractXmlSingle(Document doc, String nodePath) {
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            String xpathExpr = nodePath.replaceAll("\\[\\*\\]", "");
            return xpath.evaluate(xpathExpr, doc);
        } catch (Exception e) {
            return null;
        }
    }

    // ============ JSON 多值提取 ============

    private List<String> extractJsonValues(JsonNode root, String nodePath) {
        List<String> values = new ArrayList<>();
        if (root == null) return values;
        String path = nodePath.startsWith("/") ? nodePath.substring(1) : nodePath;
        path = path.startsWith("$.") ? path.substring(2) : path;
        path = path.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String[] parts = path.split("[./]");

        // 分离属性部分（@attr）
        String attrName = null;
        List<String> elementParts = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (part.startsWith("@")) {
                attrName = part.substring(1);
            } else {
                elementParts.add(part);
            }
        }

        collectJsonValues(root, elementParts, 0, attrName, values);
        return values;
    }

    private void collectJsonValues(JsonNode current, List<String> parts, int depth,
                                   String attrName, List<String> values) {
        if (current == null || depth > parts.size()) return;

        if (depth == parts.size()) {
            // 到达目标节点
            if (attrName != null) {
                // 目标是属性，收集所有同名的值... JSON没有属性概念，
                // 但模板路径可能包含 @，在 JSON 中当作普通字段处理
                JsonNode attrNode = current.get(attrName);
                if (attrNode != null && !attrNode.isMissingNode()) {
                    addNodeValue(attrNode, values);
                }
            } else {
                addNodeValue(current, values);
            }
            return;
        }

        String part = parts.get(depth);
        JsonNode child = current.get(part);
        if (child == null || child.isMissingNode()) return;

        if (child.isArray()) {
            for (JsonNode item : child) {
                collectJsonValues(item, parts, depth + 1, attrName, values);
            }
        } else {
            collectJsonValues(child, parts, depth + 1, attrName, values);
        }
    }

    private void addNodeValue(JsonNode node, List<String> values) {
        if (node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.isTextual() ? item.asText() : item.toString());
            }
        } else if (node.isTextual()) {
            values.add(node.asText());
        } else if (!node.isNull()) {
            values.add(node.toString());
        }
    }

    // ============ 类型校验 ============

    private boolean validateValue(TemplateNodeConstraint constraint, String actualValue,
                                  String dataType, String instancePath, ValidationResult result) {
        switch (dataType.toUpperCase()) {
            case "ENUM":
                return validateEnum(constraint, actualValue, instancePath, result);
            case "DATE":
                return validateDate(constraint, actualValue, instancePath, result);
            case "TIME":
                return validateTime(constraint, actualValue, instancePath, result);
            case "DATETIME":
                return validateDateTime(constraint, actualValue, instancePath, result);
            case "BOOLEAN":
                return validateBoolean(constraint, actualValue, instancePath, result);
            case "INTEGER":
            case "LONG":
                return validateInteger(actualValue, instancePath, dataType, result);
            case "DECIMAL":
            case "NUMBER":
                return validateDecimal(actualValue, instancePath, result);
            default:
                return true;
        }
    }

    private boolean validateEnum(TemplateNodeConstraint constraint, String actual, String instancePath, ValidationResult result) {
        String enumConfig = constraint.getEnumConfig();
        if (enumConfig == null || enumConfig.isEmpty()) return true;
        try {
            List<Map<String, String>> enumList = mapper.readValue(enumConfig,
                    new TypeReference<List<Map<String, String>>>() {});
            List<String> allowedValues = new ArrayList<>();
            for (Map<String, String> item : enumList) {
                String val = item.get("value");
                if (val != null) allowedValues.add(val);
            }
            if (!allowedValues.isEmpty() && !allowedValues.contains(actual.trim())) {
                result.addError(instancePath, "ENUM", "枚举值非法",
                        String.join(",", allowedValues), actual.trim());
                return false;
            }
        } catch (Exception e) { /* ignore */ }
        return true;
    }

    private boolean validateDate(TemplateNodeConstraint constraint, String actual, String instancePath, ValidationResult result) {
        String pattern = constraint.getFormatPattern();
        if (pattern == null || pattern.isEmpty()) pattern = "yyyyMMdd";
        return checkFormat(pattern, actual, instancePath, "DATE", "日期格式错误", result);
    }

    private boolean validateTime(TemplateNodeConstraint constraint, String actual, String instancePath, ValidationResult result) {
        String pattern = constraint.getFormatPattern();
        if (pattern == null || pattern.isEmpty()) pattern = "HHmmss";
        return checkFormat(pattern, actual, instancePath, "TIME", "时间格式错误", result);
    }

    private boolean validateDateTime(TemplateNodeConstraint constraint, String actual, String instancePath, ValidationResult result) {
        String pattern = constraint.getFormatPattern();
        if (pattern == null || pattern.isEmpty()) pattern = "yyyyMMddHHmmss";
        return checkFormat(pattern, actual, instancePath, "DATETIME", "日期时间格式错误", result);
    }

    private boolean checkFormat(String pattern, String actual, String instancePath,
                                String rule, String message, ValidationResult result) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            sdf.setLenient(false);
            sdf.parse(actual.trim());
            return true;
        } catch (Exception e) {
            result.addError(instancePath, rule, message, pattern, actual.trim());
            return false;
        }
    }

    private boolean validateBoolean(TemplateNodeConstraint constraint, String actual, String instancePath, ValidationResult result) {
        String format = constraint.getBooleanFormat();
        if (format == null || format.isEmpty()) format = "true_false";
        String[] validValues;
        switch (format) {
            case "Y_N": validValues = new String[]{"Y", "N"}; break;
            case "1_0": validValues = new String[]{"1", "0"}; break;
            case "是否": validValues = new String[]{"是", "否"}; break;
            default: validValues = new String[]{"true", "false"}; break;
        }
        String trimmed = actual.trim();
        for (String v : validValues) {
            if (v.equalsIgnoreCase(trimmed)) return true;
        }
        result.addError(instancePath, "BOOLEAN", "布尔值非法",
                String.join(",", validValues), trimmed);
        return false;
    }

    private boolean validateInteger(String actual, String instancePath, String type, ValidationResult result) {
        try { Long.parseLong(actual.trim()); return true; }
        catch (NumberFormatException e) {
            result.addError(instancePath, "TYPE", type + "格式错误", type, actual.trim());
            return false;
        }
    }

    private boolean validateDecimal(String actual, String instancePath, ValidationResult result) {
        try { Double.parseDouble(actual.trim()); return true; }
        catch (NumberFormatException e) {
            result.addError(instancePath, "TYPE", "数值格式错误", "Decimal", actual.trim());
            return false;
        }
    }
}

package com.dsl.engine.validator.impl;

import com.dsl.entity.TemplateSearchField;
import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.*;
import javax.xml.xpath.*;
import java.util.*;

public class SearchFieldValidator implements Validator {

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        List<TemplateSearchField> searchFields = context.getSearchFields();
        if (searchFields == null || searchFields.isEmpty()) return;

        for (TemplateSearchField field : searchFields) {
            String fieldPath = field.getFieldPath();
            if (fieldPath == null || fieldPath.isEmpty()) continue;

            List<String> values;
            if (context.isJson()) {
                values = extractJsonValues(context.getJsonRoot(), fieldPath);
            } else {
                values = extractXmlValues(context.getDocument(), fieldPath);
            }

            if (values.isEmpty()) {
                result.addError(fieldPath, "SEARCH", "检索字段缺失或为空", field.getFieldName(), "");
                continue;
            }

            boolean allHaveValue = true;
            for (int i = 0; i < values.size(); i++) {
                String v = values.get(i);
                if (v == null || v.trim().isEmpty()) {
                    String ipath = values.size() > 1 ? fieldPath + "[" + (i + 1) + "]" : fieldPath;
                    result.addError(ipath, "SEARCH", "检索字段缺失或为空", field.getFieldName(), "");
                    allHaveValue = false;
                }
            }
            if (allHaveValue) {
                result.addPasses(values.size());
            }
        }
    }

    // ============ XML ============

    private List<String> extractXmlValues(Document doc, String path) {
        List<String> values = new ArrayList<>();
        if (doc == null) return values;
        try {
            XPath xpath = XPathFactory.newInstance().newXPath();
            String xpathExpr = path.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
            NodeList nodes = (NodeList) xpath.evaluate(xpathExpr, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    values.add(node.getNodeValue());
                } else {
                    String text = node.getTextContent();
                    values.add(text != null ? text : "");
                }
            }
        } catch (Exception e) {
            // 回退单值
            try {
                XPath xpath = XPathFactory.newInstance().newXPath();
                String single = xpath.evaluate(path.replaceAll("\\[\\*\\]", ""), doc);
                if (single != null && !single.isEmpty()) values.add(single);
            } catch (Exception ignored) {}
        }
        return values;
    }

    // ============ JSON ============

    private List<String> extractJsonValues(JsonNode root, String path) {
        List<String> values = new ArrayList<>();
        if (root == null) return values;

        String p = path.startsWith("$.") ? path.substring(2) : path;
        p = p.startsWith("/") ? p.substring(1) : p;
        p = p.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
        String[] parts = p.split("[./]");
        List<String> elementParts = new ArrayList<>();
        for (String part : parts) {
            if (!part.isEmpty()) elementParts.add(part);
        }

        collectJsonValues(root, elementParts, 0, values);
        return values;
    }

    private void collectJsonValues(JsonNode current, List<String> parts, int depth, List<String> values) {
        if (current == null || depth > parts.size()) return;

        if (depth == parts.size()) {
            if (current.isArray()) {
                for (JsonNode item : current) {
                    addNodeValue(item, values);
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
                collectJsonValues(item, parts, depth + 1, values);
            }
        } else {
            collectJsonValues(child, parts, depth + 1, values);
        }
    }

    private void addNodeValue(JsonNode node, List<String> values) {
        if (node.isTextual()) {
            values.add(node.asText());
        } else if (!node.isNull()) {
            values.add(node.toString());
        }
    }
}

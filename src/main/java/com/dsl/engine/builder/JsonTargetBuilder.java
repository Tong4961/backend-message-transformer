package com.dsl.engine.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JsonTargetBuilder implements TargetBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private ObjectNode root;

    @Override
    public String getFormat() {
        return "JSON";
    }

    @Override
    public void setRoot(String rootName) {
        root = objectMapper.createObjectNode();
    }

    @Override
    public void setValue(String expression, Object value) {
        if (root == null) root = objectMapper.createObjectNode();
        if (expression == null || expression.isEmpty()) return;

        // Normalize: remove leading $.
        String path = expression.startsWith("$.") ? expression.substring(2) :
                      expression.startsWith("$") ? expression.substring(1) : expression;

        String[] parts = path.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            String arrayPath = null;
            int bracketIdx = part.indexOf('[');
            if (bracketIdx > 0) {
                arrayPath = part.substring(0, bracketIdx);
            }

            String key = arrayPath != null ? arrayPath : part;
            JsonNode child = current.get(key);

            if (child == null || !child.isObject()) {
                ObjectNode newNode = objectMapper.createObjectNode();
                current.set(key, newNode);
                current = newNode;
            } else {
                current = (ObjectNode) child;
            }
        }

        String lastPart = parts[parts.length - 1];
        if (value == null) {
            current.putNull(lastPart);
        } else if (value instanceof String) {
            current.put(lastPart, (String) value);
        } else if (value instanceof Integer) {
            current.put(lastPart, (Integer) value);
        } else if (value instanceof Long) {
            current.put(lastPart, (Long) value);
        } else if (value instanceof Double) {
            current.put(lastPart, (Double) value);
        } else if (value instanceof Boolean) {
            current.put(lastPart, (Boolean) value);
        } else if (value instanceof JsonNode) {
            current.set(lastPart, (JsonNode) value);
        } else {
            current.put(lastPart, String.valueOf(value));
        }
    }

    @Override
    public String build() {
        try {
            if (root == null) return "{}";
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("JSON构建失败: " + e.getMessage(), e);
        }
    }

    @Override
    public void initDefaults(String targetStructureJson, String format) {
        if (targetStructureJson == null || targetStructureJson.isEmpty()) return;
        try {
            JsonNode treeRoot = objectMapper.readTree(targetStructureJson);
            if (treeRoot.isObject()) {
                // Use the parsed sample as the starting structure (preserve original values)
                root = (ObjectNode) treeRoot;
            }
        } catch (Exception e) {
            // If parsing fails, silently skip
        }
    }

    public void setNode(String expression, JsonNode node) {
        if (root == null) root = objectMapper.createObjectNode();
        String path = expression.startsWith("$.") ? expression.substring(2) :
                      expression.startsWith("$") ? expression.substring(1) : expression;
        String[] parts = path.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            JsonNode child = current.get(part);
            if (child == null || !child.isObject()) {
                ObjectNode newNode = objectMapper.createObjectNode();
                current.set(part, newNode);
                current = newNode;
            } else {
                current = (ObjectNode) child;
            }
        }
        current.set(parts[parts.length - 1], node);
    }
}

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
                clearedArrays.clear();
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

    private final java.util.Set<String> clearedArrays = new java.util.HashSet<>();
    private final java.util.Map<String, JsonNode> arrayBlueprints = new java.util.HashMap<>();

    @Override
    public Object addArrayItem(String arrayExpression) {
        if (root == null) root = objectMapper.createObjectNode();
        String path = arrayExpression.startsWith("$.") ? arrayExpression.substring(2) :
                      arrayExpression.startsWith("$") ? arrayExpression.substring(1) : arrayExpression;
        String[] parts = path.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isArray = part.contains("[*]");

            if (isArray) {
                String key = part.replace("[*]", "");
                JsonNode arrayNode = current.get(key);
                ArrayNode array;
                if (arrayNode instanceof ArrayNode) {
                    array = (ArrayNode) arrayNode;
                } else {
                    array = objectMapper.createArrayNode();
                    current.set(key, array);
                }

                // Save blueprint from first template item before clearing
                if (!clearedArrays.contains(key) && array.size() > 0) {
                    JsonNode first = array.get(0);
                    if (first.isObject()) {
                        arrayBlueprints.put(key, first);
                    }
                }

                // Clear template sample items on first add
                if (!clearedArrays.contains(key)) {
                    array.removeAll();
                    clearedArrays.add(key);
                }

                if (i == parts.length - 1) {
                    // Final segment: append new item, using blueprint if available
                    ObjectNode item;
                    JsonNode blueprint = arrayBlueprints.get(key);
                    if (blueprint != null) {
                        item = (ObjectNode) blueprint.deepCopy();
                    } else {
                        item = objectMapper.createObjectNode();
                    }
                    array.add(item);
                    return item;
                } else {
                    // Intermediate: navigate into last existing item
                    if (array.size() > 0) {
                        JsonNode last = array.get(array.size() - 1);
                        if (last.isObject()) {
                            current = (ObjectNode) last;
                        } else {
                            ObjectNode newObj = objectMapper.createObjectNode();
                            array.add(newObj);
                            current = newObj;
                        }
                    } else {
                        ObjectNode newObj;
                        JsonNode blueprint = arrayBlueprints.get(key);
                        if (blueprint != null) {
                            newObj = (ObjectNode) blueprint.deepCopy();
                        } else {
                            newObj = objectMapper.createObjectNode();
                        }
                        array.add(newObj);
                        current = newObj;
                    }
                }
            } else {
                // Normal object field
                if (i == parts.length - 1) {
                    // Final segment: create/ensure array and append item
                    JsonNode existingNode = current.get(part);
                    ArrayNode array;
                    if (existingNode instanceof ArrayNode) {
                        // Already an array
                        array = (ArrayNode) existingNode;
                    } else if (existingNode != null && existingNode.isObject()) {
                        // Convert existing object to array, preserving its content
                        array = objectMapper.createArrayNode();
                        array.add(existingNode.deepCopy());
                        current.set(part, array);
                    } else {
                        // Create new array
                        array = objectMapper.createArrayNode();
                        current.set(part, array);
                    }
                    // Save blueprint from first template item before clearing
                    if (!clearedArrays.contains(part) && array.size() > 0) {
                        JsonNode first = array.get(0);
                        if (first.isObject()) {
                            arrayBlueprints.put(part, first);
                        }
                    }
                    if (!clearedArrays.contains(part)) {
                        // Don't clear if we just converted from object
                        if (!(existingNode != null && existingNode.isObject())) {
                            array.removeAll();
                        }
                        clearedArrays.add(part);
                    }
                    ObjectNode item;
                    JsonNode blueprint = arrayBlueprints.get(part);
                    if (blueprint != null) {
                        item = (ObjectNode) blueprint.deepCopy();
                    } else {
                        item = objectMapper.createObjectNode();
                    }
                    array.add(item);
                    return item;
                } else {
                    JsonNode child = current.get(part);
                    if (child == null || !child.isObject()) {
                        ObjectNode newNode = objectMapper.createObjectNode();
                        current.set(part, newNode);
                        current = newNode;
                    } else {
                        current = (ObjectNode) child;
                    }
                }
            }
        }
        return current;
    }

    @Override
    public java.util.List<Object> getArrayItems(String arrayExpression) {
        if (root == null) return null;
        String path = arrayExpression.startsWith("$.") ? arrayExpression.substring(2) :
                      arrayExpression.startsWith("$") ? arrayExpression.substring(1) : arrayExpression;
        String[] parts = path.split("\\.");
        ObjectNode current = root;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            boolean isArray = part.contains("[*]");

            if (isArray) {
                String key = part.replace("[*]", "");
                JsonNode arrayNode = current.get(key);
                if (!(arrayNode instanceof ArrayNode)) return null;
                ArrayNode array = (ArrayNode) arrayNode;

                if (i == parts.length - 1) {
                    // Final segment: return all items
                    java.util.List<Object> items = new java.util.ArrayList<>();
                    for (JsonNode item : array) {
                        items.add(item);
                    }
                    return items;
                } else {
                    // Intermediate: navigate into last item
                    if (array.size() == 0) return null;
                    JsonNode last = array.get(array.size() - 1);
                    if (!last.isObject()) return null;
                    current = (ObjectNode) last;
                }
            } else {
                if (i == parts.length - 1) {
                    JsonNode existingNode = current.get(part);
                    if (existingNode instanceof ArrayNode) {
                        // Already an array
                        java.util.List<Object> items = new java.util.ArrayList<>();
                        for (JsonNode item : (ArrayNode) existingNode) {
                            items.add(item);
                        }
                        return items;
                    } else if (existingNode != null && existingNode.isObject()) {
                        // Convert object to array and return items
                        ArrayNode array = objectMapper.createArrayNode();
                        array.add(existingNode.deepCopy());
                        current.set(part, array);
                        clearedArrays.add(part); // Mark as cleared to prevent double clearing
                        java.util.List<Object> items = new java.util.ArrayList<>();
                        for (JsonNode item : array) {
                            items.add(item);
                        }
                        return items;
                    }
                    return null;
                } else {
                    JsonNode child = current.get(part);
                    if (child == null || !child.isObject()) return null;
                    current = (ObjectNode) child;
                }
            }
        }
        return null;
    }

    @Override
    public Object addArrayItemInParent(Object parentItem, String relativePath) {
        if (!(parentItem instanceof ObjectNode)) return null;
        ObjectNode parent = (ObjectNode) parentItem;
        String path = relativePath.startsWith("$.") ? relativePath.substring(2) :
                      relativePath.startsWith("$") ? relativePath.substring(1) : relativePath;
        String[] parts = path.split("\\.");

        ObjectNode current = parent;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == parts.length - 1) {
                // Final segment: create/ensure array and append item
                JsonNode existingNode = current.get(part);
                ArrayNode array;
                if (existingNode instanceof ArrayNode) {
                    array = (ArrayNode) existingNode;
                } else if (existingNode != null && existingNode.isObject()) {
                    // Convert existing object to array
                    array = objectMapper.createArrayNode();
                    array.add(existingNode.deepCopy());
                    current.set(part, array);
                } else {
                    array = objectMapper.createArrayNode();
                    current.set(part, array);
                }
                // Save blueprint from first template item before clearing
                String clearKey = System.identityHashCode(parent) + "/" + part;
                if (!clearedArrays.contains(clearKey) && array.size() > 0) {
                    JsonNode first = array.get(0);
                    if (first.isObject()) {
                        arrayBlueprints.put(clearKey, first);
                    }
                }
                // Clear template sample items on first add per parent
                if (!clearedArrays.contains(clearKey)) {
                    array.removeAll();
                    clearedArrays.add(clearKey);
                }
                ObjectNode item;
                JsonNode blueprint = arrayBlueprints.get(clearKey);
                if (blueprint != null) {
                    item = (ObjectNode) blueprint.deepCopy();
                } else {
                    item = objectMapper.createObjectNode();
                }
                array.add(item);
                return item;
            } else {
                // Intermediate: navigate or create
                JsonNode child = current.get(part);
                if (child == null || !child.isObject()) {
                    ObjectNode newNode = objectMapper.createObjectNode();
                    current.set(part, newNode);
                    current = newNode;
                } else {
                    current = (ObjectNode) child;
                }
            }
        }
        return null;
    }

    @Override
    public void setValueInArrayItem(Object arrayItem, String relativePath, Object value) {
        if (!(arrayItem instanceof ObjectNode)) return;
        ObjectNode item = (ObjectNode) arrayItem;
        String path = relativePath.startsWith("$.") ? relativePath.substring(2) :
                      relativePath.startsWith("$") ? relativePath.substring(1) : relativePath;
        String[] parts = path.split("\\.");

        ObjectNode current = item;
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
}

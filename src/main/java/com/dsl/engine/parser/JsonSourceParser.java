package com.dsl.engine.parser;

import com.dsl.engine.model.NodeWithParent;
import com.dsl.engine.model.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.jayway.jsonpath.JsonPath;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JsonSourceParser implements SourceParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getFormat() {
        return "JSON";
    }

    @Override
    public List<TreeNode> parseStructure(String data) {
        try {
            JsonNode root = objectMapper.readTree(data);
            List<TreeNode> result = new ArrayList<>();
            TreeNode rootNode = new TreeNode("$", "$", "OBJECT");
            parseNode(root, "$", rootNode.getChildren());
            result.add(rootNode);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("JSON解析失败: " + e.getMessage(), e);
        }
    }

    private void parseNode(JsonNode node, String parentPath, List<TreeNode> result) {
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String fieldName = entry.getKey();
                JsonNode child = entry.getValue();
                String childPath = parentPath + "." + fieldName;

                TreeNode treeNode = new TreeNode(fieldName, childPath, "ELEMENT");

                if (child.isArray()) {
                    treeNode.setArray(true);
                    treeNode.setDataType("ARRAY");
                    if (child.size() > 0) {
                        JsonNode first = child.get(0);
                        if (first.isObject()) {
                            parseNode(first, childPath + "[*]", treeNode.getChildren());
                        } else {
                            treeNode.setDataType(inferJsonType(first));
                            treeNode.setSampleValue(first.asText());
                        }
                    }
                } else if (child.isObject()) {
                    treeNode.setDataType("OBJECT");
                    parseNode(child, childPath, treeNode.getChildren());
                } else {
                    treeNode.setDataType(inferJsonType(child));
                    if (!child.isNull()) {
                        treeNode.setSampleValue(child.asText());
                    }
                }
                result.add(treeNode);
            }
        }
    }

    private String inferJsonType(JsonNode node) {
        if (node.isInt()) return "INTEGER";
        if (node.isLong()) return "LONG";
        if (node.isFloat() || node.isDouble()) return "DECIMAL";
        if (node.isBoolean()) return "BOOLEAN";
        return "STRING";
    }

    @Override
    public Object extractValue(String data, String expression) {
        try {
            return JsonPath.read(data, expression);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<String> extractArray(String data, String expression) {
        List<String> items = new ArrayList<>();
        try {
            Object result = JsonPath.read(data, expression);
            if (result instanceof List) {
                for (Object item : (List<?>) result) {
                    items.add(item == null ? "" : String.valueOf(item));
                }
            } else if (result != null) {
                items.add(String.valueOf(result));
            }
        } catch (Exception e) {
            // ignore
        }
        return items;
    }

    @Override
    public List<Object> extractNodeContexts(String data, String expression) {
        List<Object> nodes = new ArrayList<>();
        try {
            // Parse to JsonNode tree, then navigate to the array
            JsonNode root = objectMapper.readTree(data);
            String path = expression.startsWith("$.") ? expression.substring(2) :
                          expression.startsWith("$") ? expression.substring(1) : expression;
            String[] parts = path.split("\\.");
            JsonNode current = root;
            JsonNode parent = null;
            for (String part : parts) {
                if (current == null) break;
                parent = current;
                current = current.get(part);
            }
            if (current != null && current.isArray()) {
                for (int i = 0; i < current.size(); i++) {
                    // Generate parentId based on parent's field name in grandparent
                    String parentId = null;
                    if (parent != null) {
                        // Find the field name of parent in its parent
                        JsonNode grandParent = null;
                        JsonNode temp = root;
                        for (int j = 0; j < parts.length - 1; j++) {
                            grandParent = temp;
                            temp = temp.get(parts[j]);
                        }
                        if (grandParent != null) {
                            // Use the last part as parent identifier
                            parentId = parts[parts.length - 1];
                        }
                    }
                    nodes.add(new NodeWithParent(current.get(i), parent, parentId));
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return nodes;
    }

    @Override
    public Object extractValueFromContext(Object context, String expression) {
        if (context == null) return null;
        // Unwrap NodeWithParent
        if (context instanceof NodeWithParent) {
            context = ((NodeWithParent) context).getNode();
        }
        try {
            if (context instanceof JsonNode) {
                JsonNode node = (JsonNode) context;
                if (expression == null || expression.isEmpty() || ".".equals(expression)) {
                    return node.isTextual() ? node.asText() : node.toString();
                }
                JsonNode child = node.get(expression);
                if (child != null) {
                    return child.isTextual() ? child.asText() : child.toString();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    @Override
    public Object getParentContext(Object context) {
        if (context instanceof NodeWithParent) {
            // Return parentId for grouping, not the parent node object
            String parentId = ((NodeWithParent) context).getParentId();
            return parentId != null ? parentId : ((NodeWithParent) context).getParent();
        }
        return null;
    }
}

package com.dsl.engine.parser;

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
            parseNode(root, "$", result);
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
}

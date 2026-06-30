package com.dsl.engine.validator.impl;

import com.dsl.entity.TemplateNodeConfig;
import com.dsl.engine.model.TreeNode;
import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.*;
import java.util.*;

public class LoopValidator implements Validator {

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        List<TemplateNodeConfig> nodeConfigs = context.getNodeConfigs();
        if (nodeConfigs == null) return;

        List<TreeNode> templateTree = context.getTemplateStructure();
        if (templateTree == null) return;

        for (TemplateNodeConfig config : nodeConfigs) {
            if (!Boolean.TRUE.equals(config.getIsLoop())) continue;

            String loopPath = config.getNodePath();
            if (loopPath == null || loopPath.isEmpty()) continue;

            TreeNode loopNode = findTreeNode(templateTree, loopPath);
            if (loopNode == null || loopNode.getChildren() == null || loopNode.getChildren().isEmpty()) {
                continue;
            }

            if (context.isJson()) {
                validateJsonLoop(context, result, config, loopNode, loopPath);
            } else {
                validateXmlLoop(context, result, config, loopNode, loopPath);
            }
        }
    }

    // ============ XML ============

    private void validateXmlLoop(ValidationContext context, ValidationResult result,
                                  TemplateNodeConfig config, TreeNode loopNode, String loopPath) {
        Document doc = context.getDocument();
        if (doc == null) return;

        // 找到循环节点对应的所有元素
        List<Element> items = findXmlElements(doc.getDocumentElement(), loopPath);
        if (items.isEmpty()) {
            result.addError(loopPath, "LOOP", "循环节点不存在: " + config.getNodeName());
            return;
        }

        // 检查每个元素的子节点
        for (int i = 0; i < items.size(); i++) {
            Element item = items.get(i);
            String itemPath = loopPath + (items.size() > 1 ? "[" + (i + 1) + "]" : "");
            for (TreeNode child : loopNode.getChildren()) {
                if ("ATTRIBUTE".equals(child.getNodeType())) {
                    String attrName = child.getName().startsWith("@") ? child.getName().substring(1) : child.getName();
                    if (!item.hasAttribute(attrName)) {
                        result.addError(itemPath + "/@" + attrName, "LOOP",
                                "循环元素缺少属性: " + attrName);
                    }
                } else {
                    // 只查直接子元素
                    boolean found = false;
                    NodeList children = item.getChildNodes();
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j) instanceof Element
                                && ((Element) children.item(j)).getTagName().equals(child.getName())) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) {
                        result.addError(itemPath + "/" + child.getName(), "LOOP",
                                "循环元素缺少子节点: " + child.getName());
                    }
                }
            }
        }
        result.addPass();
    }

    /**
     * 根据路径查找 XML 元素列表
     * 路径格式: /root/parent/child 或 /root/parent/child/subchild
     * 每段只找直接子元素
     */
    private List<Element> findXmlElements(Element root, String path) {
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String[] parts = cleanPath.split("/");

        // 去掉根元素名（如果路径以根元素名开头）
        int startIdx = 0;
        if (parts.length > 1 && parts[0].equals(root.getTagName())) {
            startIdx = 1;
        }

        List<Element> current = Collections.singletonList(root);
        for (int i = startIdx; i < parts.length; i++) {
            // 剥离 [idx] 索引，只取标签名匹配
            String tagName = parts[i].replaceAll("\\[\\d+\\]", "");
            List<Element> next = new ArrayList<>();
            for (Element el : current) {
                NodeList children = el.getChildNodes();
                for (int j = 0; j < children.getLength(); j++) {
                    if (children.item(j) instanceof Element
                            && ((Element) children.item(j)).getTagName().equals(tagName)) {
                        next.add((Element) children.item(j));
                    }
                }
            }
            current = next;
            if (current.isEmpty()) break;
        }
        return current;
    }

    // ============ JSON ============

    private void validateJsonLoop(ValidationContext context, ValidationResult result,
                                   TemplateNodeConfig config, TreeNode loopNode, String loopPath) {
        JsonNode root = context.getJsonRoot();
        if (root == null) return;

        // 找到循环节点对应的 JSON 节点（可能是数组或单个对象）
        JsonNode target = findJsonNode(root, loopPath);
        if (target == null) {
            result.addError(loopPath, "LOOP", "循环节点不存在: " + config.getNodeName());
            return;
        }

        // 确定要检查的元素列表
        List<JsonNode> items = new ArrayList<>();
        if (target.isArray()) {
            for (JsonNode item : target) items.add(item);
        } else if (target.isObject()) {
            items.add(target);
        }

        if (items.isEmpty()) {
            result.addError(loopPath, "LOOP", "循环节点为空: " + config.getNodeName());
            return;
        }

        // 检查每个元素的子节点
        for (int i = 0; i < items.size(); i++) {
            JsonNode item = items.get(i);
            String itemPath = loopPath + (items.size() > 1 ? "[" + (i + 1) + "]" : "");
            for (TreeNode child : loopNode.getChildren()) {
                // JSON 中 @ 是字段名的一部分，不像 XML 中 @ 仅表示属性引用
                String childName = child.getName();
                JsonNode childNode = item.get(childName);
                if (childNode == null || childNode.isMissingNode()) {
                    result.addError(itemPath + "." + childName, "LOOP",
                            "循环元素缺少子节点: " + childName);
                }
            }
        }
        result.addPass();
    }

    /**
     * 根据路径查找 JSON 节点
     * 路径格式: $.hospitals[*].departments 或 $.hospitals.departments
     * 支持 [*] 表示数组，自动取第一个元素继续导航
     */
    private JsonNode findJsonNode(JsonNode root, String path) {
        String cleanPath = path.startsWith("$.") ? path.substring(2) : path;
        cleanPath = cleanPath.startsWith("/") ? cleanPath.substring(1) : cleanPath;
        if (cleanPath.isEmpty()) return root;

        String[] parts = cleanPath.split("\\.");
        JsonNode current = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (current == null) return null;

            boolean hasArray = part.contains("[*]");
            String key = part.replace("[*]", "");

            current = current.get(key);
            if (current == null) return null;

            if (hasArray && current.isArray()) {
                if (current.size() > 0) {
                    current = current.get(0);
                } else {
                    return null;
                }
            }
        }
        return current;
    }

    private TreeNode findTreeNode(List<TreeNode> nodes, String path) {
        for (TreeNode node : nodes) {
            if (path.equals(node.getPath())) return node;
            if (node.getChildren() != null) {
                TreeNode found = findTreeNode(node.getChildren(), path);
                if (found != null) return found;
            }
        }
        return null;
    }
}

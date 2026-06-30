package com.dsl.engine.validator.impl;

import com.dsl.engine.model.TreeNode;
import com.dsl.entity.TemplateNodeConfig;
import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.*;
import java.util.*;

public class TemplateStructureValidator implements Validator {

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        List<TreeNode> templateTree = context.getTemplateStructure();
        if (templateTree == null || templateTree.isEmpty()) return;

        Set<String> templatePaths = new LinkedHashSet<>();
        collectPaths(templateTree, templatePaths);

        List<TemplateNodeConfig> nodeConfigs = context.getNodeConfigs();

        // 从模板树构建循环子节点每实例期望计数（只看单个实例的直属子节点数）
        Map<String, Integer> loopChildExpected = buildLoopChildExpected(templateTree, nodeConfigs);

        if (context.isJson()) {
            validateJson(context, result, templatePaths, nodeConfigs, loopChildExpected);
        } else {
            validateXml(context, result, templatePaths, nodeConfigs, loopChildExpected);
        }
    }

    private void validateXml(ValidationContext context, ValidationResult result,
                             Set<String> templatePaths, List<TemplateNodeConfig> nodeConfigs,
                             Map<String, Integer> loopChildExpected) {
        Document doc = context.getDocument();
        if (doc == null) return;

        Set<String> xmlPaths = new LinkedHashSet<>();
        Element root = doc.getDocumentElement();
        collectXmlPaths(root, "/" + root.getNodeName(), xmlPaths);
        checkUnknownNodes(xmlPaths, templatePaths, nodeConfigs, loopChildExpected, result);
        checkMissingNodes(xmlPaths, templatePaths, nodeConfigs, result);
    }

    private void validateJson(ValidationContext context, ValidationResult result,
                              Set<String> templatePaths, List<TemplateNodeConfig> nodeConfigs,
                              Map<String, Integer> loopChildExpected) {
        JsonNode root = context.getJsonRoot();
        if (root == null) return;

        Set<String> jsonPaths = new LinkedHashSet<>();
        collectJsonPaths(root, "$", jsonPaths);
        checkUnknownNodes(jsonPaths, templatePaths, nodeConfigs, loopChildExpected, result);
        checkMissingNodes(jsonPaths, templatePaths, nodeConfigs, result);
    }

    // ── 循环节点子节点每实例期望计数 ──
    // 直接用模板树中循环节点的直属子节点来算，不受模板中循环实例数量的影响
    private Map<String, Integer> buildLoopChildExpected(List<TreeNode> templateTree,
                                                         List<TemplateNodeConfig> nodeConfigs) {
        Map<String, Integer> expected = new HashMap<>();
        Set<String> loopBases = buildLoopBases(nodeConfigs);
        for (String loopBase : loopBases) {
            TreeNode loopNode = findTreeNodeByNormPath(templateTree, loopBase);
            if (loopNode != null && loopNode.getChildren() != null) {
                for (TreeNode child : loopNode.getChildren()) {
                    if (!"ATTRIBUTE".equals(child.getNodeType()) && child.getPath() != null) {
                        expected.merge(normalizePath(child.getPath()), 1, Integer::sum);
                    }
                }
            }
        }
        return expected;
    }

    // ── 未知/多余节点检测 ──

    private void checkUnknownNodes(Set<String> actualPaths, Set<String> templatePaths,
                                    List<TemplateNodeConfig> nodeConfigs,
                                    Map<String, Integer> loopChildExpected,
                                    ValidationResult result) {
        Set<String> loopBases = buildLoopBases(nodeConfigs);

        // 模板路径按 key 计数
        Map<String, Integer> templateKeyCount = new HashMap<>();
        Set<String> normTemplateKeys = new HashSet<>();
        for (String tplPath : templatePaths) {
            String key = buildKey(tplPath);
            templateKeyCount.merge(key, 1, Integer::sum);
            normTemplateKeys.add(normalizePath(key));
        }

        // 已消费计数（按 key）
        Map<String, Integer> consumed = new HashMap<>();
        // 循环子节点按父实例的消费计数: parentInstanceKey → (normChildKey → count)
        Map<String, Map<String, Integer>> loopChildConsumed = new HashMap<>();

        int passCount = 0;
        for (String actualPath : actualPaths) {
            String normActual = normalizePath(actualPath);

            // 条件1：报文路径是模板中某个节点的父容器
            if (isContainerInTemplate(normActual, templatePaths)) {
                passCount++;
                continue;
            }

            String key = buildKey(actualPath);
            Integer tplCount = templateKeyCount.getOrDefault(key, 0);
            int used = consumed.getOrDefault(key, 0);

            if (isExactLoopNode(normActual, loopBases)) {
                // 循环节点本身：不限出现次数
                passCount++;
            } else if (tplCount > 0 && used < tplCount) {
                // 精确 key 匹配且未超额
                consumed.put(key, used + 1);
                passCount++;
            } else if (tplCount > 0) {
                // 精确 key 匹配但已超额 → 多余节点
                result.addError(actualPath, "STRUCTURE", "多余节点: " + getLastName(actualPath));
            } else if (isChildOfLoop(key, loopBases, normTemplateKeys)) {
                // 父节点是循环实例 → 按每实例期望计数校验
                int lastSep = Math.max(key.lastIndexOf('/'), key.lastIndexOf('.'));
                String parentKey = key.substring(0, lastSep);
                String normChildKey = normalizePath(key);
                int expected = loopChildExpected.getOrDefault(normChildKey, 1);

                Map<String, Integer> parentConsumed = loopChildConsumed
                        .computeIfAbsent(parentKey, k -> new HashMap<>());
                int childUsed = parentConsumed.getOrDefault(normChildKey, 0);

                if (childUsed < expected) {
                    parentConsumed.put(normChildKey, childUsed + 1);
                    passCount++;
                } else {
                    result.addError(actualPath, "STRUCTURE", "多余节点: " + getLastName(actualPath));
                }
            } else {
                // 模板中完全不存在 → 未知节点
                result.addError(actualPath, "STRUCTURE", "未知节点: " + getLastName(actualPath));
            }
        }
        if (passCount > 0) result.addPasses(passCount);
    }

    // ── 缺失节点检测 ──

    private void checkMissingNodes(Set<String> actualPaths, Set<String> templatePaths,
                                    List<TemplateNodeConfig> nodeConfigs, ValidationResult result) {
        Set<String> loopBases = buildLoopBases(nodeConfigs);

        // 报文路径按 key 计数
        Map<String, Integer> actualKeyCount = new HashMap<>();
        for (String path : actualPaths) {
            actualKeyCount.merge(buildKey(path), 1, Integer::sum);
        }

        // 模板路径按 key 分组
        Map<String, List<String>> tplGroups = new LinkedHashMap<>();
        Map<String, Integer> tplKeyCount = new HashMap<>();
        Set<String> normTemplateKeys = new HashSet<>();
        for (String tplPath : templatePaths) {
            if (tplPath.contains("/@")) continue;
            String key = buildKey(tplPath);
            tplKeyCount.merge(key, 1, Integer::sum);
            tplGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(tplPath);
            normTemplateKeys.add(normalizePath(key));
        }

        for (Map.Entry<String, List<String>> entry : tplGroups.entrySet()) {
            String key = entry.getKey();
            List<String> origPaths = entry.getValue();
            int tplCount = tplKeyCount.get(key);
            int actCount = actualKeyCount.getOrDefault(key, 0);

            // 循环节点本身不限数量；循环实例的子节点随父实例数量变化
            String normKey = normalizePath(key);
            if (isExactLoopNode(normKey, loopBases)
                    || isChildOfLoop(key, loopBases, normTemplateKeys)) continue;
            if (actCount >= tplCount) continue;

            for (int i = actCount; i < origPaths.size(); i++) {
                result.addError(origPaths.get(i), "STRUCTURE", "缺少节点: " + getLastName(origPaths.get(i)));
            }
        }
    }

    // ── 辅助方法 ──

    // 报文归一化路径是否在模板中是容器（模板有非属性的子节点在此路径下）
    private boolean isContainerInTemplate(String normActual, Set<String> templatePaths) {
        String prefix = normActual + "/";
        for (String tplPath : templatePaths) {
            String normTpl = normalizePath(tplPath);
            if (normTpl.startsWith(prefix) && !normTpl.contains("/@")) {
                return true;
            }
        }
        return false;
    }

    // 构建"父上下文 + 节点名"的 key，用于区分同名但不同父节点的情况
    private String buildKey(String path) {
        int lastSep = Math.max(path.lastIndexOf('/'), path.lastIndexOf('.'));
        if (lastSep < 0) return path;
        String parent = path.substring(0, lastSep);
        String base = path.substring(lastSep + 1).replaceAll("\\[\\d+\\]", "").replaceAll("\\[\\*\\]", "");
        return parent + "/" + base;
    }

    private Set<String> buildLoopBases(List<TemplateNodeConfig> nodeConfigs) {
        Set<String> bases = new HashSet<>();
        if (nodeConfigs != null) {
            for (TemplateNodeConfig config : nodeConfigs) {
                if (Boolean.TRUE.equals(config.getIsLoop()) && config.getNodePath() != null) {
                    bases.add(normalizePath(config.getNodePath()));
                }
            }
        }
        return bases;
    }

    // 当前路径是否就是循环节点本身（非子节点）
    private boolean isExactLoopNode(String normPath, Set<String> loopBases) {
        return loopBases.contains(normPath);
    }

    // 当前路径的父节点是循环实例，且归一化后模板中有对应定义
    private boolean isChildOfLoop(String key, Set<String> loopBases, Set<String> normTemplateKeys) {
        int lastSep = Math.max(key.lastIndexOf('/'), key.lastIndexOf('.'));
        if (lastSep < 0) return false;
        String parentKey = key.substring(0, lastSep);
        String parentNorm = normalizePath(parentKey);
        return loopBases.contains(parentNorm) && normTemplateKeys.contains(normalizePath(key));
    }

    private String normalizePath(String path) {
        return path.replaceAll("\\[\\*\\]", "").replaceAll("\\[\\d+\\]", "");
    }

    // 在模板树中按归一化路径查找节点
    private TreeNode findTreeNodeByNormPath(List<TreeNode> nodes, String normPath) {
        for (TreeNode node : nodes) {
            if (node.getPath() != null && normalizePath(node.getPath()).equals(normPath)) {
                return node;
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                TreeNode found = findTreeNodeByNormPath(node.getChildren(), normPath);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collectPaths(List<TreeNode> nodes, Set<String> paths) {
        for (TreeNode node : nodes) {
            if (node.getPath() != null) {
                paths.add(node.getPath());
            }
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                collectPaths(node.getChildren(), paths);
            }
        }
    }

    private void collectXmlPaths(Element element, String parentPath, Set<String> paths) {
        paths.add(parentPath);
        NodeList children = element.getChildNodes();
        Map<String, Integer> nameCount = new HashMap<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                nameCount.merge(child.getNodeName(), 1, Integer::sum);
            }
        }
        Map<String, Integer> nameIndex = new HashMap<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = child.getNodeName();
                int idx = nameIndex.getOrDefault(name, 0);
                nameIndex.put(name, idx + 1);
                String childPath = nameCount.get(name) > 1
                        ? parentPath + "/" + name + "[" + (idx + 1) + "]"
                        : parentPath + "/" + name;
                collectXmlPaths((Element) child, childPath, paths);
            }
        }
    }

    private void collectJsonPaths(JsonNode node, String parentPath, Set<String> paths) {
        paths.add(parentPath);
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode child = entry.getValue();
                if (child.isArray()) {
                    String arrPath = parentPath + "." + key + "[*]";
                    paths.add(arrPath);
                    for (int i = 0; i < child.size(); i++) {
                        collectJsonPaths(child.get(i), arrPath, paths);
                    }
                } else if (child.isObject()) {
                    collectJsonPaths(child, parentPath + "." + key, paths);
                } else {
                    paths.add(parentPath + "." + key);
                }
            }
        }
    }

    private String getLastName(String path) {
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('.'));
        return idx >= 0 ? path.substring(idx + 1) : path;
    }
}

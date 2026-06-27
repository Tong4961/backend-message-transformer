package com.dsl.engine.parser;

import com.dsl.engine.model.TreeNode;
import java.util.List;

public interface SourceParser {
    String getFormat();
    List<TreeNode> parseStructure(String data);
    Object extractValue(String data, String expression);

    /**
     * 从源数据中提取数组（XPath NODESET 或 JsonPath 数组）
     */
    List<String> extractArray(String data, String expression);

    /**
     * 从源数据中提取节点上下文列表（用于循环内子字段提取）
     * XML返回List<Node>，JSON返回List<JsonNode>
     */
    List<Object> extractNodeContexts(String data, String expression);

    /**
     * 从上下文节点中提取值（用于循环内子字段提取）
     */
    Object extractValueFromContext(Object context, String expression);

    /**
     * 获取上下文节点的父节点（用于按父元素分组）
     */
    Object getParentContext(Object context);
}

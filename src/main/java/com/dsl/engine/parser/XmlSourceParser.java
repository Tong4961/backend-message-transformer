package com.dsl.engine.parser;

import com.dsl.engine.model.TreeNode;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.util.*;

@Component
public class XmlSourceParser implements SourceParser {

    @Override
    public String getFormat() {
        return "XML";
    }

    @Override
    public List<TreeNode> parseStructure(String data) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
            Element root = doc.getDocumentElement();
            List<TreeNode> result = new ArrayList<>();
            parseNode(root, "/" + root.getNodeName(), result);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("XML解析失败: " + e.getMessage(), e);
        }
    }

    private void parseNode(Node node, String parentPath, List<TreeNode> result) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element element = (Element) node;
            String nodeName = element.getNodeName();
            String path = parentPath;

            TreeNode treeNode = new TreeNode(nodeName, path, "ELEMENT");
            treeNode.setArray(isArrayElement(element));

            // Parse attributes
            NamedNodeMap attrs = element.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                Node attr = attrs.item(i);
                String attrPath = path + "/@" + attr.getNodeName();
                TreeNode attrNode = new TreeNode("@" + attr.getNodeName(), attrPath, "ATTRIBUTE");
                attrNode.setSampleValue(attr.getNodeValue());
                attrNode.setDataType("STRING");
                treeNode.addChild(attrNode);
            }

            // Parse child elements
            NodeList children = element.getChildNodes();
            boolean hasElementChild = false;
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    hasElementChild = true;
                    String childPath = path + "/" + child.getNodeName();
                    List<TreeNode> childResults = new ArrayList<>();
                    parseNode(child, childPath, childResults);
                    for (TreeNode cr : childResults) {
                        treeNode.addChild(cr);
                    }
                }
            }

            // If leaf node, get text content
            if (!hasElementChild && attrs.getLength() == 0) {
                String text = element.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    treeNode.setSampleValue(text.trim());
                }
                treeNode.setDataType(inferDataType(text));
            }

            result.add(treeNode);
        }
    }

    private boolean isArrayElement(Element element) {
        Node parent = element.getParentNode();
        if (parent == null) return false;
        NodeList siblings = parent.getChildNodes();
        int count = 0;
        for (int i = 0; i < siblings.getLength(); i++) {
            if (siblings.item(i).getNodeName().equals(element.getNodeName())) {
                count++;
            }
        }
        return count > 1;
    }

    private String inferDataType(String value) {
        if (value == null || value.trim().isEmpty()) return "STRING";
        try {
            Integer.parseInt(value);
            return "INTEGER";
        } catch (NumberFormatException ignored) {}
        try {
            Long.parseLong(value);
            return "LONG";
        } catch (NumberFormatException ignored) {}
        try {
            Double.parseDouble(value);
            return "DECIMAL";
        } catch (NumberFormatException ignored) {}
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    @Override
    public Object extractValue(String data, String expression) {
        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
            XPath xpath = XPathFactory.newInstance().newXPath();
            // Try as string first
            String result = xpath.evaluate(expression, doc);
            if (result == null || result.isEmpty()) {
                // Try as node set
                NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
                if (nodes.getLength() > 0) {
                    return nodes;
                }
            }
            return result;
        } catch (Exception e) {
            return null;
        }
    }
}

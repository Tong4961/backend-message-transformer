package com.dsl.engine.parser;

import com.dsl.engine.model.NodeWithParent;
import com.dsl.engine.model.TreeNode;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.ArrayList;

@Component
public class XmlSourceParser implements SourceParser {

    @Override
    public String getFormat() {
        return "XML";
    }

    /**
     * 解析 XML 为 Document，关闭命名空间感知。
     * HL7 V3 等带默认 xmlns 的 XML，XPath 无前缀表达式需匹配空命名空间元素。
     */
    private Document parseDocument(String data) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(data.getBytes("UTF-8")));
    }

    @Override
    public List<TreeNode> parseStructure(String data) {
        try {
            Document doc = parseDocument(data);
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

            // 第一遍：统计同名子元素个数，决定是否需要索引后缀
            Map<String, Integer> nameCount = new HashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    nameCount.merge(child.getNodeName(), 1, Integer::sum);
                }
            }

            // 第二遍：解析子元素，同名多个时加上 [idx] 区分路径
            Map<String, Integer> nameIndex = new HashMap<>();
            for (int i = 0; i < children.getLength(); i++) {
                Node child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    hasElementChild = true;
                    String name = child.getNodeName();
                    int idx = nameIndex.getOrDefault(name, 0);
                    nameIndex.put(name, idx + 1);
                    String childPath = nameCount.get(name) > 1
                            ? path + "/" + name + "[" + (idx + 1) + "]"
                            : path + "/" + name;
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
            Document doc = parseDocument(data);
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

    @Override
    public List<String> extractArray(String data, String expression) {
        List<String> items = new ArrayList<>();
        try {
            Document doc = parseDocument(data);
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                items.add(nodes.item(i).getTextContent());
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
            Document doc = parseDocument(data);
            XPath xpath = XPathFactory.newInstance().newXPath();
            NodeList nodeList = (NodeList) xpath.evaluate(expression, doc, XPathConstants.NODESET);
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                Node parent = node.getParentNode();
                // DOM spec: Attr.getParentNode() returns null, use getOwnerElement() instead
                if (parent == null && node.getNodeType() == Node.ATTRIBUTE_NODE) {
                    parent = ((Attr) node).getOwnerElement();
                }
                Object parentCtx = (parent instanceof Element) ? parent : null;
                // Generate parentId based on parent's position in its parent
                String parentId = null;
                if (parent instanceof Element) {
                    Element parentElem = (Element) parent;
                    // Use parent's tag name + index as identifier
                    Node grandParent = parent.getParentNode();
                    if (grandParent instanceof Element) {
                        NodeList siblings = ((Element) grandParent).getElementsByTagName(parentElem.getTagName());
                        for (int j = 0; j < siblings.getLength(); j++) {
                            if (siblings.item(j) == parent) {
                                parentId = parentElem.getTagName() + "[" + j + "]";
                                break;
                            }
                        }
                    } else {
                        parentId = parentElem.getTagName() + "[0]";
                    }
                }
                nodes.add(new NodeWithParent(node, parentCtx, parentId));
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
        if (context instanceof Node) {
            Node node = (Node) context;
            // If expression is empty, return text content of current node
            if (expression == null || expression.isEmpty()) {
                return node.getTextContent();
            }
            // Handle Attr nodes directly: return the attribute value
            if (node instanceof Attr) {
                Attr attr = (Attr) node;
                if (expression.equals("@" + attr.getName()) || expression.equals(attr.getName())) {
                    return attr.getValue();
                }
                return null;
            }
            // Handle attributes: @attrName
            if (expression.startsWith("@") && node instanceof Element) {
                Element elem = (Element) node;
                String attrName = expression.substring(1);
                return elem.getAttribute(attrName);
            }
            // Handle path expressions (e.g., "telecom/value" or "telecom")
            if (node instanceof Element) {
                Element elem = (Element) node;
                String[] parts = expression.split("/");
                Element current = elem;
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    if (part.isEmpty()) continue;
                    // @attr must be the last segment, applied to current element
                    if (part.startsWith("@")) {
                        if (i == parts.length - 1) {
                            return current.getAttribute(part.substring(1));
                        }
                        return null;
                    }
                    // Find child element with matching tag name
                    NodeList children = current.getChildNodes();
                    Element found = null;
                    for (int j = 0; j < children.getLength(); j++) {
                        if (children.item(j) instanceof Element) {
                            Element child = (Element) children.item(j);
                            if (child.getTagName().equals(part)) {
                                found = child;
                                break;
                            }
                        }
                    }
                    if (found == null) {
                        return null;
                    }
                    if (i == parts.length - 1) {
                        return found.getTextContent();
                    }
                    current = found;
                }
            }
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
        if (context instanceof Node) {
            Node parent = ((Node) context).getParentNode();
            if (parent instanceof Element) {
                return parent;
            }
        }
        return null;
    }
}

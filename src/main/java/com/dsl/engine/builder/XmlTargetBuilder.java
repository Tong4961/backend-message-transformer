package com.dsl.engine.builder;

import org.springframework.stereotype.Component;
import org.w3c.dom.*;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.util.*;

@Component
public class XmlTargetBuilder implements TargetBuilder {

    private Document document;
    private Element rootElement;
    private boolean hasTemplate = false;

    @Override
    public String getFormat() {
        return "XML";
    }

    @Override
    public void setRoot(String rootName) {
        try {
            document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            rootElement = document.createElement(rootName);
            document.appendChild(rootElement);
        } catch (Exception e) {
            throw new RuntimeException("XML根节点创建失败", e);
        }
    }

    @Override
    public void setValue(String expression, Object value) {
        if (rootElement == null) return;
        if (expression == null || expression.isEmpty()) return;

        // Normalize path: strip leading /
        String path = expression.startsWith("/") ? expression.substring(1) : expression;
        String[] parts = path.split("/");

        // If path starts with root element name, strip it (path is relative to root)
        int startIdx = 0;
        if (parts.length > 1 && parts[0].equals(rootElement.getNodeName())) {
            startIdx = 1;
        }

        // Build normalized parts array (excluding root element name)
        String[] normParts = new String[parts.length - startIdx];
        System.arraycopy(parts, startIdx, normParts, 0, normParts.length);

        if (hasTemplate) {
            walkAndReplace(rootElement, normParts, 0, value);
        } else {
            walkAndCreate(rootElement, normParts, 0, value);
        }
    }

    /**
     * Template mode: replace value at the target path. Skip if path doesn't exist.
     */
    private boolean walkAndReplace(Element current, String[] parts, int index, Object value) {
        if (index >= parts.length) return false;

        String part = parts[index];

        if (part.startsWith("@")) {
            String attrName = part.substring(1);
            if (current.hasAttribute(attrName)) {
                current.setAttribute(attrName, value == null ? "" : String.valueOf(value));
            }
            return true;
        }

        Element child = findChild(current, part);
        if (child == null) {
            return false;
        }

        if (index == parts.length - 1) {
            child.setTextContent(value == null ? "" : String.valueOf(value));
            return true;
        }

        return walkAndReplace(child, parts, index + 1, value);
    }

    /**
     * No-template mode: create nodes as needed.
     */
    private void walkAndCreate(Element current, String[] parts, int index, Object value) {
        if (index >= parts.length) return;

        String part = parts[index];

        if (part.startsWith("@")) {
            String attrName = part.substring(1);
            current.setAttribute(attrName, value == null ? "" : String.valueOf(value));
            return;
        }

        Element child = findChild(current, part);
        if (child == null) {
            child = document.createElement(part);
            current.appendChild(child);
        }

        if (index == parts.length - 1) {
            child.setTextContent(value == null ? "" : String.valueOf(value));
        } else {
            walkAndCreate(child, parts, index + 1, value);
        }
    }

    @Override
    public void initDefaults(String targetStructureXml, String format) {
        if (targetStructureXml == null || targetStructureXml.isEmpty() || rootElement == null) return;
        try {
            Document sampleDoc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(targetStructureXml.getBytes("UTF-8")));
            Element sampleRoot = sampleDoc.getDocumentElement();

            // Import the entire sample structure (preserves namespaces and original values)
            Element newRoot = (Element) document.importNode(sampleRoot, true);
            document.replaceChild(newRoot, rootElement);
            rootElement = newRoot;
            hasTemplate = true;
            clearedArrays.clear();
        } catch (Exception e) {
            // If parsing fails, silently skip
        }
    }

    private boolean hasChildElements(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) return true;
        }
        return false;
    }

    private Element findChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(name)) {
                return (Element) child;
            }
        }
        return null;
    }

    // Track arrays that have been cleared for loop usage
    private final java.util.Set<String> clearedArrays = new java.util.HashSet<>();

    @Override
    public Object addArrayItem(String arrayExpression) {
        if (rootElement == null || document == null) return null;
        String path = arrayExpression.startsWith("/") ? arrayExpression.substring(1) : arrayExpression;
        String[] parts = path.split("/");
        int startIdx = 0;
        if (parts.length > 1 && parts[0].equals(rootElement.getNodeName())) {
            startIdx = 1;
        }
        String[] normParts = new String[parts.length - startIdx];
        System.arraycopy(parts, startIdx, normParts, 0, normParts.length);

        Element current = rootElement;
        for (int i = 0; i < normParts.length; i++) {
            String part = normParts[i];
            Element child = findChild(current, part);
            if (child == null) {
                child = document.createElement(part);
                current.appendChild(child);
            }
            if (i < normParts.length - 1) {
                current = child;
            }
        }

        // Clear existing template array siblings on first loop add
        String tagName = normParts[normParts.length - 1];
        if (!clearedArrays.contains(tagName)) {
            Node parentNode = current.getParentNode();
            Element parent;
            if (parentNode instanceof Element) {
                parent = (Element) parentNode;
            } else {
                parent = rootElement;
            }
            NodeList siblings = parent.getChildNodes();
            java.util.List<Node> toRemove = new java.util.ArrayList<>();
            for (int i = 0; i < siblings.getLength(); i++) {
                Node sibling = siblings.item(i);
                boolean isArrayElement = sibling.getNodeType() == Node.ELEMENT_NODE
                        && sibling.getNodeName().equals(tagName);
                boolean isWhitespace = sibling.getNodeType() == Node.TEXT_NODE
                        && sibling.getTextContent().trim().isEmpty();
                if (isArrayElement || isWhitespace) {
                    toRemove.add(sibling);
                }
            }
            for (Node n : toRemove) {
                parent.removeChild(n);
            }
            clearedArrays.add(tagName);
            current = parent;
        }

        // Create a new array item element
        Element newItem = document.createElement(normParts[normParts.length - 1]);
        current.appendChild(newItem);
        return newItem;
    }

    @Override
    public java.util.List<Object> getArrayItems(String arrayExpression) {
        if (rootElement == null || document == null) return null;
        String path = arrayExpression.startsWith("/") ? arrayExpression.substring(1) : arrayExpression;
        String[] parts = path.split("/");
        int startIdx = 0;
        if (parts.length > 1 && parts[0].equals(rootElement.getNodeName())) {
            startIdx = 1;
        }
        String[] normParts = new String[parts.length - startIdx];
        System.arraycopy(parts, startIdx, normParts, 0, normParts.length);

        // Navigate to parent of the array element
        Element current = rootElement;
        for (int i = 0; i < normParts.length - 1; i++) {
            Element child = findChild(current, normParts[i]);
            if (child == null) return null;
            current = child;
        }

        // Collect all child elements with the target tag name
        String tagName = normParts[normParts.length - 1];
        java.util.List<Object> items = new java.util.ArrayList<>();
        NodeList children = current.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                items.add(child);
            }
        }
        return items.isEmpty() ? null : items;
    }

    @Override
    public Object addArrayItemInParent(Object parentItem, String relativePath) {
        if (!(parentItem instanceof Element) || document == null) return null;
        Element parent = (Element) parentItem;
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        String[] parts = path.split("/");

        Element current = parent;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (i == parts.length - 1) {
                // Clear existing template siblings on first add (per parent element)
                String clearKey = System.identityHashCode(parent) + "/" + part;
                if (!clearedArrays.contains(clearKey)) {
                    NodeList siblings = current.getChildNodes();
                    java.util.List<Node> toRemove = new java.util.ArrayList<>();
                    for (int j = 0; j < siblings.getLength(); j++) {
                        Node sibling = siblings.item(j);
                        boolean isArrayElement = sibling.getNodeType() == Node.ELEMENT_NODE
                                && sibling.getNodeName().equals(part);
                        boolean isWhitespace = sibling.getNodeType() == Node.TEXT_NODE
                                && sibling.getTextContent().trim().isEmpty();
                        if (isArrayElement || isWhitespace) {
                            toRemove.add(sibling);
                        }
                    }
                    for (Node n : toRemove) {
                        current.removeChild(n);
                    }
                    clearedArrays.add(clearKey);
                }
                // Create array element and append
                Element newItem = document.createElement(part);
                current.appendChild(newItem);
                return newItem;
            } else {
                // Intermediate: navigate or create
                Element child = findChild(current, part);
                if (child == null) {
                    child = document.createElement(part);
                    current.appendChild(child);
                }
                current = child;
            }
        }
        return null;
    }

    @Override
    public void setValueInArrayItem(Object arrayItem, String relativePath, Object value) {
        if (!(arrayItem instanceof Element)) return;
        Element item = (Element) arrayItem;
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        String[] parts = path.split("/");

        String lastPart = parts[parts.length - 1];

        Element current = item;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            if (part.startsWith("@")) {
                current.setAttribute(part.substring(1), value == null ? "" : String.valueOf(value));
                return;
            }
            Element child = document.createElement(part);
            current.appendChild(child);
            current = child;
        }

        if (lastPart.startsWith("@")) {
            item.setAttribute(lastPart.substring(1), value == null ? "" : String.valueOf(value));
        } else {
            Element leaf = document.createElement(lastPart);
            leaf.setTextContent(value == null ? "" : String.valueOf(value));
            item.appendChild(leaf);
        }
    }

    @Override
    public String build() {
        try {
            if (document == null) return "";
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new RuntimeException("XML构建失败: " + e.getMessage(), e);
        }
    }
}

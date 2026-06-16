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

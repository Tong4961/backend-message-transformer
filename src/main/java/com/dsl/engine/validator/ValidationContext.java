package com.dsl.engine.validator;

import com.dsl.entity.TemplateNodeConfig;
import com.dsl.entity.TemplateNodeConstraint;
import com.dsl.entity.TemplateSearchField;
import com.dsl.engine.model.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import java.util.List;

public class ValidationContext {
    private String content;
    private String format;  // XML, JSON, HL7_V3
    private Document document;  // XML 解析结果
    private JsonNode jsonRoot;  // JSON 解析结果
    private String rootElementName;
    private List<TreeNode> templateStructure;
    private List<TemplateNodeConstraint> constraints;
    private List<TemplateNodeConfig> nodeConfigs;
    private List<TemplateSearchField> searchFields;

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public Document getDocument() { return document; }
    public void setDocument(Document document) { this.document = document; }
    public JsonNode getJsonRoot() { return jsonRoot; }
    public void setJsonRoot(JsonNode jsonRoot) { this.jsonRoot = jsonRoot; }
    public String getRootElementName() { return rootElementName; }
    public void setRootElementName(String rootElementName) { this.rootElementName = rootElementName; }
    public List<TreeNode> getTemplateStructure() { return templateStructure; }
    public void setTemplateStructure(List<TreeNode> templateStructure) { this.templateStructure = templateStructure; }
    public List<TemplateNodeConstraint> getConstraints() { return constraints; }
    public void setConstraints(List<TemplateNodeConstraint> constraints) { this.constraints = constraints; }
    public List<TemplateNodeConfig> getNodeConfigs() { return nodeConfigs; }
    public void setNodeConfigs(List<TemplateNodeConfig> nodeConfigs) { this.nodeConfigs = nodeConfigs; }
    public List<TemplateSearchField> getSearchFields() { return searchFields; }
    public void setSearchFields(List<TemplateSearchField> searchFields) { this.searchFields = searchFields; }

    public boolean isXml() {
        return "XML".equals(format) || "HL7_V3".equals(format);
    }

    public boolean isJson() {
        return "JSON".equals(format);
    }
}

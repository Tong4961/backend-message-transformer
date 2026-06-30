package com.dsl.engine.validator.impl;

import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;

public class XmlSyntaxValidator implements Validator {

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        String content = context.getContent();
        if (content == null || content.trim().isEmpty()) {
            result.addError("/", "SYNTAX", "XML内容为空");
            return;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(new ByteArrayInputStream(content.getBytes("UTF-8")));
            context.setDocument(doc);
            context.setRootElementName(doc.getDocumentElement().getNodeName());
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = "未知解析错误";
            // 简化错误信息
            if (msg.contains("line")) {
                try {
                    String lineStr = msg.substring(msg.indexOf("line"), msg.indexOf(".", msg.indexOf("line")));
                    result.addError("/", "SYNTAX", "XML语法错误: " + lineStr, null, msg);
                } catch (Exception ex) {
                    result.addError("/", "SYNTAX", "XML语法错误: " + msg);
                }
            } else {
                result.addError("/", "SYNTAX", "XML语法错误: " + msg);
            }
        }
    }
}

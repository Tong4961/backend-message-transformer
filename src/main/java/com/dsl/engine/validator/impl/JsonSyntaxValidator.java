package com.dsl.engine.validator.impl;

import com.dsl.engine.validator.ValidationContext;
import com.dsl.engine.validator.ValidationResult;
import com.dsl.engine.validator.Validator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JsonSyntaxValidator implements Validator {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    @Override
    public void validate(ValidationContext context, ValidationResult result) {
        String content = context.getContent();
        if (content == null || content.trim().isEmpty()) {
            result.addError("/", "SYNTAX", "JSON内容为空");
            return;
        }

        try {
            JsonNode root = mapper.readTree(content);
            context.setJsonRoot(root);
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) msg = "未知解析错误";
            // 简化 Jackson 错误信息
            if (msg.length() > 200) {
                msg = msg.substring(0, 200) + "...";
            }
            result.addError("/", "SYNTAX", "JSON语法错误: " + msg);
        }
    }
}

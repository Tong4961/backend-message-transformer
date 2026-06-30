package com.dsl.engine.validator;

import com.dsl.engine.validator.impl.*;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

@Component
public class MessageValidationEngine {

    private final XmlSyntaxValidator xmlSyntaxValidator = new XmlSyntaxValidator();
    private final JsonSyntaxValidator jsonSyntaxValidator = new JsonSyntaxValidator();
    private final TemplateStructureValidator structureValidator = new TemplateStructureValidator();
    private final NodeConstraintValidator constraintValidator = new NodeConstraintValidator();
    private final SearchFieldValidator searchFieldValidator = new SearchFieldValidator();
    private final LoopValidator loopValidator = new LoopValidator();

    public ValidationResult validate(ValidationContext context) {
        ValidationResult result = new ValidationResult();

        // 第一步：语法校验（按格式选择）
        if (context.isJson()) {
            jsonSyntaxValidator.validate(context, result);
        } else {
            xmlSyntaxValidator.validate(context, result);
        }
        if (result.hasErrors()) return result;

        // 后续校验共用同一套逻辑
        structureValidator.validate(context, result);
        constraintValidator.validate(context, result);
        searchFieldValidator.validate(context, result);
        loopValidator.validate(context, result);

        return result;
    }
}

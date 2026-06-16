package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;

@Component
public class TypeCastConverter implements FunctionConverter {
    @Override
    public String getName() { return "cast"; }

    @Override
    public Object convert(Object input, String... params) {
        if (input == null) return null;
        if (params.length < 1) return input;
        String targetType = params[0].toUpperCase();
        String str = String.valueOf(input);
        switch (targetType) {
            case "INTEGER":
            case "INT":
                return Integer.parseInt(str);
            case "LONG":
                return Long.parseLong(str);
            case "DECIMAL":
            case "DOUBLE":
                return new BigDecimal(str);
            case "BOOLEAN":
                return Boolean.parseBoolean(str);
            case "STRING":
                return str;
            case "DATE":
                String format = params.length > 1 ? params[1] : "yyyy-MM-dd";
                try {
                    return new SimpleDateFormat(format).parse(str);
                } catch (Exception e) {
                    return str;
                }
            default:
                return input;
        }
    }
}

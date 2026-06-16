package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

@Component
public class ConcatConverter implements FunctionConverter {
    @Override
    public String getName() { return "concat"; }

    @Override
    public Object convert(Object input, String... params) {
        StringBuilder sb = new StringBuilder(input == null ? "" : String.valueOf(input));
        for (String param : params) {
            sb.append(param);
        }
        return sb.toString();
    }
}

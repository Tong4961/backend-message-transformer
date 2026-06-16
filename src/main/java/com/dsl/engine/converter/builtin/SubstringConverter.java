package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

@Component
public class SubstringConverter implements FunctionConverter {
    @Override
    public String getName() { return "substring"; }

    @Override
    public Object convert(Object input, String... params) {
        if (input == null) return null;
        String str = String.valueOf(input);
        int begin = params.length > 0 ? Integer.parseInt(params[0]) : 0;
        int end = params.length > 1 ? Integer.parseInt(params[1]) : str.length();
        if (begin < 0) begin = 0;
        if (end > str.length()) end = str.length();
        return str.substring(begin, end);
    }
}

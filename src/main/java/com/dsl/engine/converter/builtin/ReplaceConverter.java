package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

@Component
public class ReplaceConverter implements FunctionConverter {
    @Override
    public String getName() { return "replace"; }

    @Override
    public Object convert(Object input, String... params) {
        if (input == null) return null;
        if (params.length < 2) return input;
        return String.valueOf(input).replace(params[0], params[1]);
    }
}

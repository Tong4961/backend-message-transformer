package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

@Component
public class TrimConverter implements FunctionConverter {
    @Override
    public String getName() { return "trim"; }

    @Override
    public Object convert(Object input, String... params) {
        return input == null ? null : String.valueOf(input).trim();
    }
}

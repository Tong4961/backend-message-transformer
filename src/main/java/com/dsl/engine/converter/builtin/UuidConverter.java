package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UuidConverter implements FunctionConverter {
    @Override
    public String getName() { return "uuid"; }

    @Override
    public Object convert(Object input, String... params) {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

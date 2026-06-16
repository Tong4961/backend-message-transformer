package com.dsl.engine.converter.builtin;

import com.dsl.engine.converter.FunctionConverter;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class Base64Converter implements FunctionConverter {
    @Override
    public String getName() { return "base64"; }

    @Override
    public Object convert(Object input, String... params) {
        if (input == null) return null;
        String mode = params.length > 0 ? params[0] : "encode";
        String str = String.valueOf(input);
        if ("decode".equalsIgnoreCase(mode)) {
            return new String(Base64.getDecoder().decode(str));
        }
        return Base64.getEncoder().encodeToString(str.getBytes());
    }
}

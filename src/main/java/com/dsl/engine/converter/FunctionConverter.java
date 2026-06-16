package com.dsl.engine.converter;

public interface FunctionConverter {
    String getName();
    Object convert(Object input, String... params);
}

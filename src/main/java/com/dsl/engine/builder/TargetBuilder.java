package com.dsl.engine.builder;

import java.util.List;
import java.util.Map;

public interface TargetBuilder {
    String getFormat();
    void setRoot(String rootName);
    void setValue(String expression, Object value);
    void initDefaults(String targetStructureJson, String format);
    String build();
}

package com.dsl.engine.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class TransformContext {
    private String sourceData;
    private String sourceFormat;
    private String targetFormat;
    private String traceId;
    private Map<String, Object> variables = new HashMap<>();
    private StringBuilder logBuffer = new StringBuilder();

    public void addLog(String message) {
        logBuffer.append("[").append(System.currentTimeMillis()).append("] ").append(message).append("\n");
    }

    public String getLogs() {
        return logBuffer.toString();
    }
}

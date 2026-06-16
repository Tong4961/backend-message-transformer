package com.dsl.engine.model;

import lombok.Data;

@Data
public class TransformResult {
    private boolean success;
    private String outputData;
    private String errorMessage;
    private String logs;
    private long duration;
    private String traceId;
}

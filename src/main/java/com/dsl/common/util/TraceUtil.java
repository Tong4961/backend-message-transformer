package com.dsl.common.util;

import java.util.UUID;

public class TraceUtil {
    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static String getTraceId() {
        String id = TRACE_ID.get();
        if (id == null) {
            id = generateTraceId();
            TRACE_ID.set(id);
        }
        return id;
    }

    public static void setTraceId(String traceId) {
        TRACE_ID.set(traceId);
    }

    public static void clear() {
        TRACE_ID.remove();
    }
}

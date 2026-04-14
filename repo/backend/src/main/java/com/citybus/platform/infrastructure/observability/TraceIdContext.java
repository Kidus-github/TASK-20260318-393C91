package com.citybus.platform.infrastructure.observability;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceIdContext {
    private TraceIdContext() {
    }

    public static String current() {
        return MDC.get(TraceIdFilter.TRACE_ID);
    }

    public static String currentOrCreate() {
        String traceId = current();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            MDC.put(TraceIdFilter.TRACE_ID, traceId);
        }
        return traceId;
    }

    public static void restore(String previousTraceId) {
        if (previousTraceId == null || previousTraceId.isBlank()) {
            MDC.remove(TraceIdFilter.TRACE_ID);
        } else {
            MDC.put(TraceIdFilter.TRACE_ID, previousTraceId);
        }
    }
}

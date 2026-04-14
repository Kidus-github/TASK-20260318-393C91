package com.citybus.platform.infrastructure.observability;

import com.citybus.platform.application.RequestMetricsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "traceId";
    private final RequestMetricsService requestMetricsService;

    public TraceIdFilter(RequestMetricsService requestMetricsService) {
        this.requestMetricsService = requestMetricsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        long startedAt = System.currentTimeMillis();
        MDC.put(TRACE_ID, traceId);
        response.addHeader("X-Trace-Id", traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            requestMetricsService.record(request.getRequestURI(), System.currentTimeMillis() - startedAt, traceId);
            requestMetricsService.recordResponse(request.getRequestURI(), response.getStatus(), traceId);
            MDC.remove(TRACE_ID);
        }
    }
}

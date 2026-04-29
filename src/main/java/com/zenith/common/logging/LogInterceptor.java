package com.zenith.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

@Component
public class LogInterceptor implements HandlerInterceptor {

    private static final String CORRELATION_ID_HEADER_NAME = "X-Correlation-ID";
    private static final String CORRELATION_ID_LOG_VAR_NAME = "traceId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER_NAME);
        if (correlationId == null || correlationId.isEmpty()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put(CORRELATION_ID_LOG_VAR_NAME, correlationId);
        response.setHeader(CORRELATION_ID_HEADER_NAME, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(CORRELATION_ID_LOG_VAR_NAME);
    }
}

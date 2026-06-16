package com.smart.retry.test.web;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;


import java.io.IOException;
import java.util.UUID;

/**
 * @Author xiaoqiang
 * @Version TraceIdFilter.java, v 0.1 2026年06月15日 traceId
 * @Description: 从请求头提取 traceId 写入 MDC（兼容多种命名），回填响应头；
 * 请求结束后清理 MDC，避免线程复用导致 traceId 串扰。
 *
 * <p>候选请求头按命中率从高到低：X-Trace-Id / X-Request-Id / traceId / TraceId；</p>
 * <p>全空时 UUID 兜底（去掉短横线，便于日志/链路展示）。</p>
 */
@Component
@Order(1)
public class TraceIdFilter extends OncePerRequestFilter {

    /** 请求头候选项 */
    private static final String[] HEADER_CANDIDATES = {
            "X-Trace-Id", "X-Request-Id", "traceId", "TraceId"
    };

    /** 回写到响应头的 traceId 名 */
    private static final String RESPONSE_HEADER = "X-Trace-Id";

    /** 同时写入 3 个 MDC key，便于兼容各种日志 pattern 与 trace 体系 */
    private static final String[] MDC_KEYS = {"traceId", "TRACE_ID", "X-Trace-Id"};

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId(request);
        for (String key : MDC_KEYS) {
            MDC.put(key, traceId);
        }
        try {
            response.setHeader(RESPONSE_HEADER, traceId);
            filterChain.doFilter(request, response);
        } finally {
            for (String key : MDC_KEYS) {
                MDC.remove(key);
            }
        }
    }

    /**
     * 顺序遍历候选请求头解析 traceId；全空时 UUID 兜底（去掉短横线）。
     */
    private String resolveTraceId(HttpServletRequest request) {
        for (String header : HEADER_CANDIDATES) {
            String value = request.getHeader(header);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}

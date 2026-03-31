package com.billiard.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_REQUESTS = 20;
    private static final long WINDOW_MS = 60_000L;
    private static final int CLEANUP_INTERVAL = 100;

    private final Map<String, Deque<Long>> requestLog = new ConcurrentHashMap<>();
    private final AtomicLong requestCounter = new AtomicLong();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/auth/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (requestCounter.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            cleanup();
        }

        String clientIp = resolveClientIp(request);
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = requestLog.computeIfAbsent(clientIp, k -> new ConcurrentLinkedDeque<>());

        while (!timestamps.isEmpty() && timestamps.peekFirst() < now - WINDOW_MS) {
            timestamps.pollFirst();
        }

        if (timestamps.size() >= MAX_REQUESTS) {
            response.setStatus(429);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }

        timestamps.addLast(now);
        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void cleanup() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        requestLog.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(ts -> ts < cutoff);
            return entry.getValue().isEmpty();
        });
    }
}

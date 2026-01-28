package com.github.spud.tinystore.account.infrastructure.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(1) // 指定过滤器顺序
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:";
    private static final int MAX_REQUESTS_PER_MINUTE = 100;
    private static final int BLOCK_DURATION_MINUTES = 15;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String requestUri = request.getRequestURI();
        String rateLimitKey = RATE_LIMIT_KEY_PREFIX + clientIp + ":" + requestUri;

        // 检查是否被封禁
        if (Boolean.TRUE.equals(redisTemplate.hasKey("block:" + clientIp))) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("请求过于频繁，请15分钟后再试");
            return;
        }

        // 统计请求次数
        Long requestCount = redisTemplate.opsForValue().increment(rateLimitKey);
        if (requestCount == 1) {
            redisTemplate.expire(rateLimitKey, 1, TimeUnit.MINUTES);
        }

        // 检查是否超过限制
        if (requestCount > MAX_REQUESTS_PER_MINUTE) {
            redisTemplate.opsForValue().set("block:" + clientIp, "blocked", BLOCK_DURATION_MINUTES, TimeUnit.MINUTES);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("请求过于频繁，请15分钟后再试");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
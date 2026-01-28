package com.github.spud.tinystore.account.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(0) // 指定过滤器顺序，比 RateLimitFilter 更早执行
public class InternalCallAuthenticationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_PREFIX = "/internal/";
    private static final String INTERNAL_TOKEN_HEADER = "X-Tinystore-Internal-Token";

    private final String internalToken;

    public InternalCallAuthenticationFilter(
            @Value("${tinystore.internal.call-token:changeit}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path) || !path.startsWith(INTERNAL_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!StringUtils.hasText(token) || !token.equals(internalToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "InternalToken");
            return;
        }

        var auth = new UsernamePasswordAuthenticationToken(
                "internal",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}


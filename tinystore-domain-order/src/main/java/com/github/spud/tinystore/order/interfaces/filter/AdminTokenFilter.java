package com.github.spud.tinystore.order.interfaces.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.spud.tinystore.order.infrastructure.config.AdminSecurityProperties;
import com.github.spud.tinystore.order.interfaces.dto.response.OrderHttpResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Admin Token Authentication Filter
 *
 * <p>Validates X-Admin-Token header for internal admin endpoints.
 */
@Slf4j
@Component
public class AdminTokenFilter extends OncePerRequestFilter {

    private final AdminSecurityProperties adminSecurityProperties;
    private final ObjectMapper objectMapper;

    public AdminTokenFilter(AdminSecurityProperties adminSecurityProperties, ObjectMapper objectMapper) {
        this.adminSecurityProperties = adminSecurityProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Only validate token for /internal/** paths
        if (!requestPath.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedToken = request.getHeader("X-Admin-Token");
        String expectedToken = adminSecurityProperties.token();

        if (providedToken == null || !providedToken.equals(expectedToken)) {
            log.warn("Unauthorized access attempt to admin endpoint: path={}, remoteAddr={}",
                requestPath, request.getRemoteAddr());

            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            OrderHttpResponse<String> errorResponse = OrderHttpResponse.fail(
                401, "Invalid or missing X-Admin-Token header");

            response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            return;
        }

        filterChain.doFilter(request, response);
    }
}

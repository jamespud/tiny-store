package com.github.spud.tinystore.account.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditLogRepository auditLogRepository;

    @Before("execution(* com.github.spud.tinystore.account.interfaces.rest..*(..))")
    public void logBefore(JoinPoint joinPoint) {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String clientIp = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String requestUri = request.getRequestURI();
            String method = request.getMethod();

            log.info("Request: {} {} from {} with User-Agent: {}", method, requestUri, clientIp, userAgent);
        }
    }

    @AfterReturning(pointcut = "execution(* com.github.spud.tinystore.account.interfaces.rest..*(..))", returning = "result")
    public void logAfterReturning(JoinPoint joinPoint, Object result) {
        saveAuditLog(joinPoint, result, null, true);
    }

    @AfterThrowing(pointcut = "execution(* com.github.spud.tinystore.account.interfaces.rest..*(..))", throwing = "exception")
    public void logAfterThrowing(JoinPoint joinPoint, Exception exception) {
        saveAuditLog(joinPoint, null, exception, false);
    }

    private void saveAuditLog(JoinPoint joinPoint, Object result, Exception exception, boolean success) {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return;
            }

            HttpServletRequest request = attributes.getRequest();
            String clientIp = getClientIp(request);
            String userAgent = request.getHeader("User-Agent");
            String requestUri = request.getRequestURI();
            String method = request.getMethod();
            String params = Arrays.toString(joinPoint.getArgs());

            AuditLog auditLog = new AuditLog();
            auditLog.setRequestUri(requestUri);
            auditLog.setHttpMethod(method);
            auditLog.setClientIp(clientIp);
            auditLog.setUserAgent(userAgent);
            auditLog.setRequestParams(params);
            auditLog.setSuccess(success);
            auditLog.setErrorMessage(exception != null ? exception.getMessage() : null);
            auditLog.setCreateTime(LocalDateTime.now());

            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
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
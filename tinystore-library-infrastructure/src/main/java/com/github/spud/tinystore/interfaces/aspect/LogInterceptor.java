package com.github.spud.tinystore.interfaces.aspect;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

/**
 * 全局日志拦截器：修正HTTP日志格式 + 带短横线log-id + 精准耗时统计
 */
@Slf4j
public class LogInterceptor implements HandlerInterceptor {

  private static final ThreadLocal<Long> REQUEST_START_NANO = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> HAS_EXCEPTION = new ThreadLocal<>();

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
    Object handler) {
    String logId = UUID.randomUUID().toString();
    MDC.put(LogConstant.MDC_LOG_ID, logId);
    // 2. 初始化开始时间和异常标记
    REQUEST_START_NANO.set(System.nanoTime());
    HAS_EXCEPTION.set(false);
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
    Object handler, Exception ex) {
    try {
      long startNano = REQUEST_START_NANO.get() == null ? 0 : REQUEST_START_NANO.get();
      double costMs = (System.nanoTime() - startNano) / 1_000_000.0;
      String costStr = String.format("%.6fms", costMs);

      String clientIp = request.getRemoteAddr();
      String requestMethod = request.getMethod();
      String requestUri = request.getRequestURI();
      int status = response.getStatus();
      String bizCode = getBizCode(handler);
      int errorCode = (ex != null || HAS_EXCEPTION.get() || !is2xx(status)) ? LogConstant.ERROR_CODE
        : LogConstant.SUCCESS_CODE;
      String lang = getRequestLang(request);

      log.info("| {} | {} | {} | {} | {} | {} | {} | {} | {} | {}",
        request.getScheme(),
        request.getLocalAddr() + ":" + request.getLocalPort(),
        status,
        costStr,
        clientIp,
        requestMethod,
        requestUri,
        bizCode,
        errorCode,
        lang
      );
    } finally {
      REQUEST_START_NANO.remove();
      HAS_EXCEPTION.remove();
      MDC.remove(LogConstant.MDC_LOG_ID);
    }
  }

  private boolean is2xx(int status) {
    return status >= 200 && status < 300;
  }

  private String getRequestLang(HttpServletRequest request) {
    String lang = request.getHeader("Accept-Language");
    if (!StringUtils.hasText(lang)) {
      return "en-US";
    }
    lang = lang.split(",")[0].trim();

    return lang.contains("-") ? lang : lang + "-" + lang.toUpperCase(Locale.ENGLISH);
  }

  private String getBizCode(Object handler) {
    return LogConstant.DEFAULT_BIZ_CODE;
  }

  @Override
  public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
    ModelAndView modelAndView) {
  }
}
package com.github.spud.tinystore.gateway.filter;

import com.github.spud.tinystore.interfaces.aspect.LogConstant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Gateway日志过滤器：为WebFlux环境设置logId
 * 使用Reactor Context传递logId，避免MDC在异步环境下丢失
 */
@Slf4j
@Component
public class GatewayLogFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    
    // 生成或获取logId
    String logId = request.getHeaders().getFirst(LogConstant.HEADER_TRACE_ID);
    if (!StringUtils.hasText(logId)) {
      logId = UUID.randomUUID().toString();
    }
    
    final String finalLogId = logId;
    
    // 使用Reactor Context传递logId（适用于WebFlux异步环境）
    return chain.filter(exchange)
      .contextWrite(Context.of(LogConstant.MDC_LOG_ID, finalLogId))
      .transformDeferredContextual((call, ctx) -> {
        // 在每个操作中设置MDC（用于同步日志输出）
        if (ctx.hasKey(LogConstant.MDC_LOG_ID)) {
          MDC.put(LogConstant.MDC_LOG_ID, ctx.get(LogConstant.MDC_LOG_ID));
        }
        return call.doFinally(signalType -> MDC.remove(LogConstant.MDC_LOG_ID));
      });
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE; // 最高优先级，确保第一个执行
  }
}

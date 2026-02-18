package com.github.spud.tinystore.order.interfaces.rest;

import com.github.spud.tinystore.order.OrderApplication;
import com.github.spud.tinystore.order.test.it.AbstractOrderIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chaos 测试：Redis 不可用时，创建订单应返回 503 (或 500)
 * <p>
 * 测试策略：
 * - 停止 Redis 容器
 * - 调用创建订单接口（携带幂等键）
 * - 验证返回 5xx 错误（幂等服务不可用）
 * <p>
 * Note: 根据异常传播路径，可能返回 503 (IdempotencyServiceUnavailableException)
 * 或 500 (通用异常)，两者都表明服务不可用。
 */
@SpringBootTest(
    classes = OrderApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false",
        "order.idempotency.fail-on-redis-error=true"
    }
)
class TradeChaosRedisDownIT extends AbstractOrderIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createTrade_whenRedisDown_shouldReturn503() {
        // Given: 停止 Redis 容器
        getRedis().stop();

        try {
            // Given: 构造创建订单请求（符合 CreateTradeRequest 格式）
            Map<String, Object> request = new HashMap<>();
            request.put("buyerId", "buyer-123");
            request.put("addressId", "addr-456");
            request.put("orderLines", new Object[]{
                Map.of(
                    "skuId", "sku-789",
                    "shopId", "shop-001",
                    "sellerId", "seller-001",
                    "quantity", 1,
                    "priceCents", 10000L
                )
            });

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Idempotency-Key", UUID.randomUUID().toString());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(request, headers);

            // When: 调用创建订单接口
            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/trade",
                HttpMethod.POST,
                entity,
                String.class
            );

            // Then: 返回 5xx Server Error
            assertThat(response.getStatusCode().is5xxServerError()).isTrue();
            // Note: Actual exception may be caught by generic handler returning generic error message
            assertThat(response.getBody()).isNotEmpty();

        } finally {
            // 清理：重启 Redis 容器（避免影响后续测试）
            getRedis().start();
        }
    }
}

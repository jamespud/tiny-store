package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 订单行 JPA 实体
 */
@Entity
@Table(name = "order_line", schema = "tinystore_order")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderLineEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "sku_id", nullable = false, length = 64)
    private String skuId;

    @Column(name = "product_id", nullable = false, length = 64)
    private String productId;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "price_cents", nullable = false)
    private Long priceCents;

    @Column(name = "line_amount_cents", nullable = false)
    private Long lineAmountCents;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

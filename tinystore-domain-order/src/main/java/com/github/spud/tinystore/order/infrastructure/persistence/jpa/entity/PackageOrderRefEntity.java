package com.github.spud.tinystore.order.infrastructure.persistence.jpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 包裹与订单关联 JPA 实体（中间表）
 */
@Entity
@Table(name = "package_order_ref", schema = "tinystore_order", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"package_id", "order_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackageOrderRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_id", nullable = false, length = 64)
    private String packageId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}

package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.infrastrucutre.domain.order.Order;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * @author Spud
 * @date 2025/8/13
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {

}

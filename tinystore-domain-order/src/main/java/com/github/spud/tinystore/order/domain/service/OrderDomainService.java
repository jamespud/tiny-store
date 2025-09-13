package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Order;
import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Service
public class OrderDomainService {
	
	private OrderRepository repository;

	public void saveOrderLine(Order order) {
		
	}
}

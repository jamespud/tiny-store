package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/8/13
 */
@Service
public class OrderService {

	@Autowired
	private OrderRepository orderRepository;

}

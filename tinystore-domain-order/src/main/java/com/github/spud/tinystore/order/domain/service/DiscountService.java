package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Discount;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class DiscountService {

	public List<Discount> findDiscountByShopId(Set<String> shopIds) {
		return List.of();
	}
}

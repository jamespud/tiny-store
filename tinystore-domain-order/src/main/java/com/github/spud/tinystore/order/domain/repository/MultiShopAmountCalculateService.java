package com.github.spud.tinystore.order.domain.repository;

import com.github.spud.tinystore.order.domain.model.Money;
import com.github.spud.tinystore.order.domain.model.SubOrder;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;

/**
 * @author Spud
 * @date 2025/10/5
 */
@FeignClient
public interface MultiShopAmountCalculateService {

	List<SubOrder> allocatePlatformDiscount(List<SubOrder> subOrderList, Money platformDiscountTotal);
}

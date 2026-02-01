package com.github.spud.tinystore.order.application.query;

import com.github.spud.tinystore.order.domain.model.ShopOrder;
import com.github.spud.tinystore.order.domain.repository.ShopOrderRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.MerchantOrderData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 商家订单查询服务（读模型）
 */
@Slf4j
@Service
public class MerchantOrderQueryService {

    @Autowired
    private ShopOrderRepository shopOrderRepository;

    /**
     * 查询商家订单详情
     * 
     * @param orderId 订单ID
     * @return 商家订单数据
     */
    public MerchantOrderData getMerchantOrder(String orderId) {
        ShopOrder order = shopOrderRepository.findByOrderId(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        return MerchantOrderData.builder()
            .orderId(order.getOrderId())
            .tradeId(order.getTradeId())
            .shopId(order.getShopId())
            .sellerId(order.getSellerId())
            .orderStatus(order.getOrderStatus().name())
            .createdAt(order.getCreatedAt().toString())
            .build();
    }
}

package com.github.spud.tinystore.order.domain.service;

import com.github.spud.tinystore.order.domain.model.Order;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * @author Spud
 * @date 2025/9/13
 */
@Component
public class OrderOutboxService {

    public List<String> recordEvent(Order event) {
        return null;
    }
}

package com.github.spud.tinystore.order.domain.service;

import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/9/6
 */
@Service
public class CancelDecisionService {

    public boolean canCancel(String orderId) {
        return true;
    }

}

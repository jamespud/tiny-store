package com.github.spud.tinystore.order.application.query;

import com.github.spud.tinystore.order.domain.model.AfterSaleCase;
import com.github.spud.tinystore.order.domain.repository.AfterSaleCaseRepository;
import com.github.spud.tinystore.order.interfaces.dto.response.AfterSaleCaseData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 售后案件查询服务（读模型）
 */
@Slf4j
@Service
public class AfterSaleQueryService {

    @Autowired
    private AfterSaleCaseRepository afterSaleCaseRepository;

    /**
     * 查询售后案件详情
     * 
     * @param caseId 案件ID
     * @return 售后案件数据
     */
    public AfterSaleCaseData getAfterSaleCase(String caseId) {
        AfterSaleCase saleCase = afterSaleCaseRepository.findByCaseId(caseId)
            .orElseThrow(() -> new RuntimeException("AfterSale case not found: " + caseId));

        return AfterSaleCaseData.builder()
            .caseId(saleCase.getCaseId())
            .tradeId(saleCase.getTradeId())
            .orderId(saleCase.getOrderId())
            .caseType(saleCase.getCaseType().name())
            .caseStatus(saleCase.getCaseStatus().name())
            .createdAt(saleCase.getCreatedAt().toString())
            .build();
    }
}

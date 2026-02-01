package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 售后案件数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AfterSaleCaseData {
    private String caseId;
    private String tradeId;
    private String orderId;
    private String caseType;
    private String caseStatus;
    private String createdAt;
}

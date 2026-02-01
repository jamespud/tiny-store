package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 申请售后响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplyAfterSaleData {
    private String caseId;
}

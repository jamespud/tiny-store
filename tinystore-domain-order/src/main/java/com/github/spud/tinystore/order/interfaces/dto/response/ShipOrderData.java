package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发货响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipOrderData {
    private String packageId;
    private String waybillNo;
}

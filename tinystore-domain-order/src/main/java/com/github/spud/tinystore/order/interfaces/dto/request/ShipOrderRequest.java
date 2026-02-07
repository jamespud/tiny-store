package com.github.spud.tinystore.order.interfaces.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商家发货请求
 */
@Data
public class ShipOrderRequest {
    @NotBlank(message = "Package ID is required")
    private String packageId;
    
    @NotBlank(message = "Waybill number is required")
    private String waybillNo;
    
    @NotBlank(message = "Logistics company is required")
    private String logistics;
    
    private String traceId;
}

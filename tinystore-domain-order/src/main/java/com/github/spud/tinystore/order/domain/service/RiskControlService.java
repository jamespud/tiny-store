package com.github.spud.tinystore.order.domain.service;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Service;

/**
 * @author Spud
 * @date 2025/10/6
 */
@Service
public class RiskControlService {

  public OrderRiskCheckResponse checkOrderRisk(OrderRiskCheckRequest request) {
    return null;
  }

  @Builder
  public static class OrderRiskCheckRequest {

    private String userId;
    private String addressId;
    private List<MerchantRiskDto> merchantList;
  }

  @Getter
  public static class OrderRiskCheckResponse {

    private boolean pass;
    private String reason;
  }

  @AllArgsConstructor
  public static class MerchantRiskDto {

    private String merchantId;
    private List<SkuRiskDTO> skuList;

  }

  @Getter
  @AllArgsConstructor
  public static class SkuRiskDTO {

    private String skuId;
    private Integer quantity;

  }
}

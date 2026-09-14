package com.github.spud.tinystore.order.interfaces.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建交易响应数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTradeData {
    private String tradeId;
    private Long payableAmountCents;
    private String paymentIntentId;
    /**
     * 优惠的最终裁决状态（C13）：下单成功 ≠ 券/优惠已经最终确定。
     *
     * <p>促销承诺是异步裁定的（{@code order.promotion.commit-async-enabled=true} 时经 Outbox 提交，
     * 由 promotion 回执 ack 才落 COMMITTED），多个买家抢同一张券时只有一个赢家，输家会变成
     * {@code FAILED} 并自动关单。所以响应必须把这个状态暴露出来，客户端才不会把 200 理解成
     * "折扣已经拿到手"：{@code PENDING} = 仍在裁决，{@code COMMITTED} = 已确定，{@code FAILED} = 输掉。
     */
    private String promotionCommitStatus;
}

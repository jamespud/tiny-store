package com.github.spud.tinystore.order.domain.rule;

import com.github.spud.tinystore.order.domain.enums.AfterSaleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("售后状态迁移规则单元测试")
class AfterSaleStatusRulesTest {

    @Test
    @DisplayName("已申请 -> 已审批 - 允许迁移")
    void isTransitionAllowed_appliedToApproved_shouldReturnTrue() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.APPLIED, 
            AfterSaleStatus.APPROVED
        )).isTrue();
    }

    @Test
    @DisplayName("已申请 -> 已拒绝 - 允许迁移")
    void isTransitionAllowed_appliedToRejected_shouldReturnTrue() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.APPLIED, 
            AfterSaleStatus.REJECTED
        )).isTrue();
    }

    @Test
    @DisplayName("已审批 -> 退款中 - 允许迁移")
    void isTransitionAllowed_approvedToRefunding_shouldReturnTrue() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.APPROVED, 
            AfterSaleStatus.REFUNDING
        )).isTrue();
    }

    @Test
    @DisplayName("退款中 -> 已退款 - 允许迁移")
    void isTransitionAllowed_refundingToRefunded_shouldReturnTrue() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.REFUNDING, 
            AfterSaleStatus.REFUNDED
        )).isTrue();
    }

    @Test
    @DisplayName("已退款 -> 任何状态 - 禁止迁移（终态）")
    void isTransitionAllowed_refundedToAny_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.REFUNDED, 
            AfterSaleStatus.APPLIED
        )).isFalse();
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.REFUNDED, 
            AfterSaleStatus.APPROVED
        )).isFalse();
    }

    @Test
    @DisplayName("已拒绝 -> 任何状态 - 禁止迁移（终态）")
    void isTransitionAllowed_rejectedToAny_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.REJECTED, 
            AfterSaleStatus.APPROVED
        )).isFalse();
    }

    @Test
    @DisplayName("已关闭 -> 任何状态 - 禁止迁移（终态）")
    void isTransitionAllowed_closedToAny_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.CLOSED, 
            AfterSaleStatus.REFUNDED
        )).isFalse();
    }

    @Test
    @DisplayName("非法跳跃 - 已申请 直接到 已退款 - 禁止迁移")
    void isTransitionAllowed_illegalJump_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.APPLIED, 
            AfterSaleStatus.REFUNDED
        )).isFalse();
    }

    @Test
    @DisplayName("null状态 - 边界条件处理")
    void isTransitionAllowed_nullStatus_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            null, 
            AfterSaleStatus.APPROVED
        )).isFalse();
        
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            AfterSaleStatus.APPLIED, 
            null
        )).isFalse();
        
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            (AfterSaleStatus) null, 
            (AfterSaleStatus) null
        )).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
        "APPLIED, APPROVED, true",
        "APPLIED, REJECTED, true",
        "APPROVED, REFUNDING, true",
        "APPROVED, CLOSED, true",
        "REFUNDING, REFUNDED, true",
        "REFUNDING, CLOSED, true",
        "APPLIED, REFUNDED, false",
        "REJECTED, APPROVED, false",
        "REFUNDED, APPLIED, false"
    })
    @DisplayName("字符串版本 - 状态码迁移验证")
    void isTransitionAllowed_stringVersion_shouldMatchEnumVersion(
        String currentCode, 
        String targetCode, 
        boolean expected
    ) {
        // When
        boolean result = AfterSaleStatusRules.isTransitionAllowed(currentCode, targetCode);
        
        // Then
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("字符串版本 - 非法状态码 - 异常处理")
    void isTransitionAllowed_invalidStatusCode_shouldReturnFalse() {
        // When & Then
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            "INVALID_CODE", 
            "APPROVED"
        )).isFalse();
        
        assertThat(AfterSaleStatusRules.isTransitionAllowed(
            "APPLIED", 
            "INVALID_CODE"
        )).isFalse();
    }
}

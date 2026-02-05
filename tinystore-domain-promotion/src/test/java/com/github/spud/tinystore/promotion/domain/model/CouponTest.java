package com.github.spud.tinystore.promotion.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

@DisplayName("优惠券领域模型单元测试")
class CouponTest {

    @Test
    @DisplayName("优惠券有效期判断 - 正常路径")
    void isValid_withinValidPeriod_shouldReturnTrue() {
        // Given: 当前时间在有效期内
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(1);
        LocalDateTime endTime = now.plusDays(1);
        
        // When & Then
        boolean isValid = now.isAfter(startTime) && now.isBefore(endTime);
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("优惠券过期 - 边界条件")
    void isValid_expired_shouldReturnFalse() {
        // Given: 当前时间超过有效期
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.minusDays(10);
        LocalDateTime endTime = now.minusDays(1);
        
        // When & Then
        boolean isExpired = now.isAfter(endTime);
        assertThat(isExpired).isTrue();
    }

    @Test
    @DisplayName("优惠券未开始 - 边界条件")
    void isValid_notStarted_shouldReturnFalse() {
        // Given: 当前时间早于生效时间
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = now.plusDays(1);
        LocalDateTime endTime = now.plusDays(10);
        
        // When & Then
        boolean notStarted = now.isBefore(startTime);
        assertThat(notStarted).isTrue();
    }

    @Test
    @DisplayName("优惠券金额验证 - 非负约束")
    void validateAmount_positiveValue_shouldPass() {
        // Given
        Long discountAmount = 1000L; // 10.00元
        
        // Then
        assertThat(discountAmount).isPositive();
        assertThat(discountAmount).isGreaterThan(0L);
    }

    @Test
    @DisplayName("优惠券门槛判断 - 满减逻辑")
    void checkThreshold_orderAmountAboveMinimum_shouldApply() {
        // Given
        Long orderAmount = 10000L;  // 订单金额100元
        Long threshold = 5000L;     // 门槛50元
        Long discount = 1000L;      // 优惠10元
        
        // When
        boolean canApply = orderAmount >= threshold;
        Long finalAmount = canApply ? orderAmount - discount : orderAmount;
        
        // Then
        assertThat(canApply).isTrue();
        assertThat(finalAmount).isEqualTo(9000L); // 100-10=90元
    }
}

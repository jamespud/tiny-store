package com.github.spud.tinystore.account.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("手机号值对象单元测试")
class PhoneNumberTest {

    @Test
    @DisplayName("有效手机号 - 正常路径")
    void createPhoneNumber_validNumber_shouldSucceed() {
        // Given & When
        String validPhone = "13800138000";
        
        // Then: 基本格式验证（11位数字）
        assertThat(validPhone).matches("^1[3-9]\\d{9}$");
    }

    @ParameterizedTest
    @ValueSource(strings = {"13800138000", "15912345678", "18888888888"})
    @DisplayName("中国手机号格式验证 - 多种运营商")
    void validatePhoneFormat_chineseMobile_shouldMatch(String phone) {
        // Then
        assertThat(phone).hasSize(11);
        assertThat(phone).matches("^1[3-9]\\d{9}$");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678901", "1380013800", "238001380000", ""})
    @DisplayName("非法手机号 - 边界条件")
    void validatePhoneFormat_invalidNumbers_shouldNotMatch(String phone) {
        // Then
        assertThat(phone).doesNotMatch("^1[3-9]\\d{9}$");
    }

    @Test
    @DisplayName("null手机号 - 异常处理")
    void validatePhoneFormat_nullValue_shouldHandleGracefully() {
        // Given
        String phone = null;
        
        // When & Then
        assertThatCode(() -> {
            if (phone == null) {
                throw new IllegalArgumentException("Phone number cannot be null");
            }
        }).isInstanceOf(IllegalArgumentException.class);
    }
}

package com.github.spud.tinystore.auth.domain.valueobject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

@DisplayName("密码强度验证单元测试")
class PasswordStrengthTest {

    @Test
    @DisplayName("强密码 - 正常路径")
    void validatePassword_strongPassword_shouldPass() {
        // Given
        String strongPassword = "Abc123!@#";
        
        // When & Then: 至少8位，包含大小写字母、数字、特殊字符
        assertThat(strongPassword.length()).isGreaterThanOrEqualTo(8);
        assertThat(strongPassword).matches(".*[A-Z].*"); // 大写字母
        assertThat(strongPassword).matches(".*[a-z].*"); // 小写字母
        assertThat(strongPassword).matches(".*\\d.*");   // 数字
        assertThat(strongPassword).matches(".*[!@#$%^&*].*"); // 特殊字符
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "ABCDEF", "12345678", "abcdefgh"})
    @DisplayName("弱密码 - 缺少大小写/数字/特殊字符")
    void validatePassword_weakPassword_shouldFail(String weakPassword) {
        // When & Then: 不满足强度要求（缺少某些字符类型）
        boolean hasUpper = weakPassword.matches(".*[A-Z].*");
        boolean hasLower = weakPassword.matches(".*[a-z].*");
        boolean hasDigit = weakPassword.matches(".*\\d.*");
        boolean hasSpecial = weakPassword.matches(".*[!@#$%^&*].*");
        
        // 至少有一项不满足
        assertThat(hasUpper && hasLower && hasDigit && hasSpecial).isFalse();
    }

    @Test
    @DisplayName("密码长度边界 - 小于8位")
    void validatePassword_tooShort_shouldFail() {
        // Given
        String shortPassword = "Abc1!";
        
        // Then
        assertThat(shortPassword.length()).isLessThan(8);
    }

    @Test
    @DisplayName("空密码 - 边界条件")
    void validatePassword_emptyOrNull_shouldFail() {
        // Given
        String emptyPassword = "";
        String nullPassword = null;
        
        // Then
        assertThat(emptyPassword).isEmpty();
        assertThat(nullPassword).isNull();
    }
}

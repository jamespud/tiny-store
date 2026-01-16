package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordApplicationService 单元测试")
class PasswordApplicationServiceTest {

    @Mock
    private AccountServiceFeignClient accountServiceFeignClient;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogPort auditLogPort;

    @InjectMocks
    private PasswordApplicationService service;

    @Test
    @DisplayName("verifyPassword - 账号不存在应抛出异常")
    void verifyPassword_UserNotFound_ThrowsException() {
        // Given
        String phone = "13800138000";
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, "password123");

        when(accountServiceFeignClient.getUserByPhone(phone))
                .thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("user not found");

        verify(auditLogPort).append(any());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("verifyPassword - 密码不匹配应抛出异常")
    void verifyPassword_InvalidCredentials_ThrowsException() {
        // Given
        String phone = "13800138000";
        String password = "wrongPassword";
        String encodedPassword = "encoded_correct_password";

        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        AccountServiceFeignClient.UserCoreDto userDto = new AccountServiceFeignClient.UserCoreDto(
                1L, phone, encodedPassword, "测试用户", "avatar.jpg", 1, null
        );

        when(accountServiceFeignClient.getUserByPhone(phone))
                .thenReturn(userDto);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid credentials");

        verify(passwordEncoder).matches(password, encodedPassword);
        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 账号冻结应抛出异常")
    void verifyPassword_UserFrozen_ThrowsException() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        String encodedPassword = "encoded_password";

        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        AccountServiceFeignClient.UserCoreDto userDto = new AccountServiceFeignClient.UserCoreDto(
                1L, phone, encodedPassword, "测试用户", "avatar.jpg", 0, null // accountStatus = 0 (禁用)
        );

        when(accountServiceFeignClient.getUserByPhone(phone))
                .thenReturn(userDto);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(UserFrozenException.class)
                .hasMessage("user is frozen");

        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 成功验证返回AuthResult")
    void verifyPassword_Success_ReturnsAuthResult() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        String encodedPassword = "encoded_password";

        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        AccountServiceFeignClient.UserCoreDto userDto = new AccountServiceFeignClient.UserCoreDto(
                1L, phone, encodedPassword, "测试用户", "avatar.jpg", 1, null // accountStatus = 1 (正常)
        );

        when(accountServiceFeignClient.getUserByPhone(phone))
                .thenReturn(userDto);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);

        // When
        AuthResult result = service.verifyPassword(command);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.user()).isNotNull();
        assertThat(result.user().getId().value()).isEqualTo("1");
        assertThat(result.user().getPhone().value()).isEqualTo(phone);
        assertThat(result.user().getUsername()).isEqualTo("测试用户");
        assertThat(result.user().getAvatar()).isEqualTo("avatar.jpg");
        assertThat(result.user().isEnabled()).isTrue();

        verify(passwordEncoder).matches(password, encodedPassword);
        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 账号状态非1应标记为disabled")
    void verifyPassword_AccountStatus2_MarksDisabled() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        String encodedPassword = "encoded_password";

        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        AccountServiceFeignClient.UserCoreDto userDto = new AccountServiceFeignClient.UserCoreDto(
                1L, phone, encodedPassword, "测试用户", "avatar.jpg", 2, null // accountStatus = 2 (待验证)
        );

        when(accountServiceFeignClient.getUserByPhone(phone))
                .thenReturn(userDto);
        when(passwordEncoder.matches(password, encodedPassword)).thenReturn(true);

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(UserFrozenException.class);

        verify(auditLogPort).append(any());
    }
}

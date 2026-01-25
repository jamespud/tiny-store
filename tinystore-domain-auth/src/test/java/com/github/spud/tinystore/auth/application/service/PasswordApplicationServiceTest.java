package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.domain.exception.UserFrozenException;
import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import feign.FeignException;
import feign.Request;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordApplicationService 单元测试")
class PasswordApplicationServiceTest {

    @Mock
    private AccountServiceFeignClient accountServiceFeignClient;

    @Mock
    private AuditLogPort auditLogPort;

    private PasswordApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PasswordApplicationService(accountServiceFeignClient, auditLogPort, "changeit");
    }

    @Test
    @DisplayName("verifyPassword - 账号不存在应抛出异常")
    void verifyPassword_UserNotFound_ThrowsException() {
        // Given
        String phone = "13800138000";
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, "password123");

        when(accountServiceFeignClient.verifyCredentials(any(), any()))
                .thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid credentials");

        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 密码不匹配应抛出异常")
    void verifyPassword_InvalidCredentials_ThrowsException() {
        // Given
        String phone = "13800138000";
        String password = "wrongPassword";
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        Request request = Request.create(Request.HttpMethod.POST,
                "/internal/account/credentials/verify",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(accountServiceFeignClient.verifyCredentials(any(), any()))
                .thenThrow(new FeignException.Unauthorized("unauthorized", request, null, null));

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid credentials");

        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 账号冻结应抛出异常")
    void verifyPassword_UserFrozen_ThrowsException() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        Request request = Request.create(Request.HttpMethod.POST,
                "/internal/account/credentials/verify",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(accountServiceFeignClient.verifyCredentials(any(), any()))
                .thenThrow(new FeignException.Forbidden("forbidden", request, null, null));

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
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        when(accountServiceFeignClient.verifyCredentials(any(), any()))
                .thenReturn(new AccountServiceFeignClient.CredentialVerifyResponse(
                        1L, phone, "测试用户", "avatar.jpg", 1, 5L
                ));

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
        assertThat(result.user().getRtVersion().value()).isEqualTo(5L);
        verify(auditLogPort).append(any());
    }

    @Test
    @DisplayName("verifyPassword - 账号状态非1应标记为disabled")
    void verifyPassword_AccountStatus2_MarksDisabled() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        VerifyPasswordCommand command = new VerifyPasswordCommand(phone, password);

        Request request = Request.create(Request.HttpMethod.POST,
                "/internal/account/credentials/verify",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        when(accountServiceFeignClient.verifyCredentials(any(), any()))
                .thenThrow(new FeignException.Forbidden("forbidden", request, null, null));

        // When & Then
        assertThatThrownBy(() -> service.verifyPassword(command))
                .isInstanceOf(UserFrozenException.class);

        verify(auditLogPort).append(any());
    }
}

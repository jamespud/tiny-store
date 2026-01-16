package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.dto.SendOtpCommand;
import com.github.spud.tinystore.auth.application.dto.SendOtpResult;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import com.github.spud.tinystore.auth.interfaces.security.otp.OtpAuthenticationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = OtpController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("OtpController Web层测试")
class OtpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OtpUseCase otpUseCase;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/otp/send - 成功发送返回202")
    void sendOtp_Success_Returns202() throws Exception {
        // Given
        String phone = "13800138000";
        SendOtpResult mockResult = new SendOtpResult("138****8000");

        when(otpUseCase.sendOtp(any(SendOtpCommand.class))).thenReturn(mockResult);

        String requestBody = """
                {
                    "phone": "13800138000",
                    "requestId": "test-request-id",
                    "ipAddress": "127.0.0.1",
                    "userAgent": "TestAgent"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/auth/otp/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody)
                        .header("X-Forwarded-For", "127.0.0.1")
                        .header("User-Agent", "TestAgent"))
                .andExpect(status().isAccepted());

        verify(otpUseCase).sendOtp(any(SendOtpCommand.class));
    }

    @Test
    @DisplayName("POST /api/auth/otp/send - 缺少必填字段返回400")
    void sendOtp_MissingRequiredField_Returns400() throws Exception {
        // Given - phone字段缺失
        String requestBody = """
                {
                    "ipAddress": "127.0.0.1",
                    "userAgent": "TestAgent"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/auth/otp/send")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(otpUseCase, never()).sendOtp(any());
    }

    @Test
    @DisplayName("POST /api/auth/otp/verify - 验证成功返回200")
    void verifyOtp_Success_Returns200() throws Exception {
        // Given
        String phone = "13800138000";
        String code = "123456";

        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.getName()).thenReturn("1");
        when(mockAuth.isAuthenticated()).thenReturn(true);

        when(authenticationManager.authenticate(any(OtpAuthenticationToken.class)))
                .thenReturn(mockAuth);

        String requestBody = String.format("""
                {
                    "phone": "%s",
                    "code": "%s"
                }
                """, phone, code);

        // When & Then
        mockMvc.perform(post("/api/auth/otp/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("验证码验证成功"))
                .andExpect(jsonPath("$.userId").value("1"));

        verify(authenticationManager).authenticate(any(OtpAuthenticationToken.class));
    }

    @Test
    @DisplayName("POST /api/auth/otp/verify - 验证失败返回400")
    void verifyOtp_FailedAuthentication_Returns400() throws Exception {
        // Given
        String phone = "13800138000";
        String code = "wrong-code";

        when(authenticationManager.authenticate(any(OtpAuthenticationToken.class)))
                .thenThrow(new RuntimeException("验证码错误"));

        String requestBody = String.format("""
                {
                    "phone": "%s",
                    "code": "%s"
                }
                """, phone, code);

        // When & Then
        mockMvc.perform(post("/api/auth/otp/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.message").exists());

        verify(authenticationManager).authenticate(any(OtpAuthenticationToken.class));
    }

    @Test
    @DisplayName("POST /api/auth/otp/verify - 缺少必填字段返回400")
    void verifyOtp_MissingRequiredField_Returns400() throws Exception {
        // Given - code字段缺失
        String requestBody = """
                {
                    "phone": "13800138000"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/auth/otp/verify")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }
}

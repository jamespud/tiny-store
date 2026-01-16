package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.interfaces.dto.request.LoginRequest;
import com.github.spud.tinystore.auth.interfaces.security.password.PasswordAuthenticationToken;
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

@WebMvcTest(controllers = PasswordController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("PasswordController Web层测试")
class PasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/login/password - 成功登录返回200")
    void login_Success_Returns200() throws Exception {
        // Given
        String principal = "13800138000";
        String credential = "password123";

        Authentication mockAuth = mock(Authentication.class);
        when(mockAuth.isAuthenticated()).thenReturn(true);
        when(mockAuth.getName()).thenReturn(principal);

        when(authenticationManager.authenticate(any(PasswordAuthenticationToken.class)))
                .thenReturn(mockAuth);

        String requestBody = String.format("""
                {
                    "principal": "%s",
                    "credential": "%s",
                    "authType": "PASSWORD"
                }
                """, principal, credential);

        // When & Then
        mockMvc.perform(post("/api/auth/login/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(authenticationManager).authenticate(any(PasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("POST /api/auth/login/password - 认证失败抛出异常")
    void login_AuthenticationFails_ThrowsException() throws Exception {
        // Given
        String principal = "13800138000";
        String credential = "wrongPassword";

        when(authenticationManager.authenticate(any(PasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("Invalid credentials"));

        String requestBody = String.format("""
                {
                    "principal": "%s",
                    "credential": "%s",
                    "authType": "PASSWORD"
                }
                """, principal, credential);

        // When & Then
        mockMvc.perform(post("/api/auth/login/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isInternalServerError());

        verify(authenticationManager).authenticate(any(PasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("POST /api/auth/login/password - 缺少必填字段返回400")
    void login_MissingRequiredField_Returns400() throws Exception {
        // Given - credential字段缺失
        String requestBody = """
                {
                    "principal": "13800138000",
                    "authType": "PASSWORD"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/auth/login/password")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(authenticationManager, never()).authenticate(any());
    }
}

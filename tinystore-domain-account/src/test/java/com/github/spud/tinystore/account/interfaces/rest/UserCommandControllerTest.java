package com.github.spud.tinystore.account.interfaces.rest;

import com.github.spud.tinystore.account.application.UserAccountApplicationService;
import com.github.spud.tinystore.account.infrastructure.filter.RateLimitFilter;
import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserCommandController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserCommandController Web层测试")
class UserCommandControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountApplicationService userAccountApplicationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/account/users/register - 成功注册返回200")
    void registerUser_Success_Returns200() throws Exception {
        // Given
        UserCore mockUser = new UserCore();
        mockUser.setUserId(1L);
        mockUser.setAccount("13800138000");
        mockUser.setNickname("测试用户");

        when(userAccountApplicationService.registerUser(anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        String requestBody = """
                {
                    "phone": "13800138000",
                    "password": "password123",
                    "nickname": "测试用户"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/account/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.account").value("13800138000"))
                .andExpect(jsonPath("$.nickname").value("测试用户"));

        verify(userAccountApplicationService).registerUser("13800138000", "password123", "测试用户");
    }

    @Test
    @DisplayName("POST /api/account/users/register - 用户已存在返回400")
    void registerUser_AlreadyExists_Returns400() throws Exception {
        // Given
        when(userAccountApplicationService.registerUser(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalArgumentException("用户已存在"));

        String requestBody = """
                {
                    "phone": "13800138000",
                    "password": "password123",
                    "nickname": "测试用户"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/account/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest());

        verify(userAccountApplicationService).registerUser(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/profile - 成功更新返回200")
    void updateUserProfile_Success_Returns200() throws Exception {
        // Given
        Long userId = 1L;
        UserCore mockUser = new UserCore();
        mockUser.setUserId(userId);
        mockUser.setNickname("新昵称");

        when(userAccountApplicationService.updateUserProfile(eq(userId), anyString(), anyString(), anyString()))
                .thenReturn(mockUser);

        String requestBody = """
                {
                    "nickname": "新昵称",
                    "avatarUrl": "new.jpg",
                    "extJson": "{}"
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.nickname").value("新昵称"));

        verify(userAccountApplicationService).updateUserProfile(eq(userId), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/profile - 用户不存在返回404")
    void updateUserProfile_NotFound_Returns404() throws Exception {
        // Given
        Long userId = 999L;
        when(userAccountApplicationService.updateUserProfile(eq(userId), nullable(String.class), nullable(String.class), nullable(String.class)))
                .thenThrow(new IllegalArgumentException("用户不存在"));

        String requestBody = """
                {
                    "nickname": "新昵称"
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/password - 成功重置密码返回200")
    void resetPassword_Success_Returns200() throws Exception {
        // Given
        Long userId = 1L;
        doNothing().when(userAccountApplicationService).resetPassword(eq(userId), anyString());

        String requestBody = """
                {
                    "newPassword": "newPassword123"
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userAccountApplicationService).resetPassword(eq(userId), eq("newPassword123"));
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/password - 用户不存在返回404")
    void resetPassword_NotFound_Returns404() throws Exception {
        // Given
        Long userId = 999L;
        doThrow(new IllegalArgumentException("用户不存在"))
                .when(userAccountApplicationService).resetPassword(eq(userId), anyString());

        String requestBody = """
                {
                    "newPassword": "newPassword123"
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/password", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/status - 成功更新状态返回200")
    void updateUserStatus_Success_Returns200() throws Exception {
        // Given
        Long userId = 1L;
        doNothing().when(userAccountApplicationService).updateUserStatus(eq(userId), anyInt());

        String requestBody = """
                {
                    "accountStatus": 0
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk());

        verify(userAccountApplicationService).updateUserStatus(eq(userId), eq(0));
    }

    @Test
    @DisplayName("PUT /api/account/users/{userId}/status - 用户不存在返回404")
    void updateUserStatus_NotFound_Returns404() throws Exception {
        // Given
        Long userId = 999L;
        doThrow(new IllegalArgumentException("用户不存在"))
                .when(userAccountApplicationService).updateUserStatus(eq(userId), anyInt());

        String requestBody = """
                {
                    "accountStatus": 0
                }
                """;

        // When & Then
        mockMvc.perform(put("/api/account/users/{userId}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/account/users/{userId} - 成功删除返回200")
    void deleteUser_Success_Returns200() throws Exception {
        // Given
        Long userId = 1L;
        doNothing().when(userAccountApplicationService).deleteUser(eq(userId));

        // When & Then
        mockMvc.perform(delete("/api/account/users/{userId}", userId))
                .andExpect(status().isOk());

        verify(userAccountApplicationService).deleteUser(eq(userId));
    }

    @Test
    @DisplayName("DELETE /api/account/users/{userId} - 用户不存在返回404")
    void deleteUser_NotFound_Returns404() throws Exception {
        // Given
        Long userId = 999L;
        doThrow(new IllegalArgumentException("用户不存在"))
                .when(userAccountApplicationService).deleteUser(eq(userId));

        // When & Then
        mockMvc.perform(delete("/api/account/users/{userId}", userId))
                .andExpect(status().isNotFound());
    }
}

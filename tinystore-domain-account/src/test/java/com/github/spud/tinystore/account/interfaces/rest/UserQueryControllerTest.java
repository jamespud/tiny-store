package com.github.spud.tinystore.account.interfaces.rest;

import com.github.spud.tinystore.account.application.UserAccountApplicationService;
import com.github.spud.tinystore.account.infrastructure.filter.RateLimitFilter;
import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserQueryController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = RateLimitFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UserQueryController Web层测试")
class UserQueryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserAccountApplicationService userAccountApplicationService;

    @Test
    @DisplayName("GET /api/account/users/{userId} - 用户存在返回200")
    void getUserById_Exists_Returns200() throws Exception {
        // Given
        String userId = "1";
        UserCore mockUser = new UserCore();
        mockUser.setUserId(userId);
        mockUser.setAccount("13800138000");
        mockUser.setNickname("测试用户");

        when(userAccountApplicationService.getUserById(userId)).thenReturn(Optional.of(mockUser));

        // When & Then
        mockMvc.perform(get("/api/account/users/{userId}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.account").value("13800138000"))
                .andExpect(jsonPath("$.nickname").value("测试用户"));

        verify(userAccountApplicationService).getUserById(userId);
    }

    @Test
    @DisplayName("GET /api/account/users/{userId} - 用户不存在返回404")
    void getUserById_NotExists_Returns404() throws Exception {
        // Given
        String userId = "999";
        when(userAccountApplicationService.getUserById(userId)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/account/users/{userId}", userId))
                .andExpect(status().isNotFound());

        verify(userAccountApplicationService).getUserById(userId);
    }

    @Test
    @DisplayName("GET /api/account/users/phone/{phone} - 用户存在返回200")
    void getUserByPhone_Exists_Returns200() throws Exception {
        // Given
        String phone = "13800138000";
        UserCore mockUser = new UserCore();
        mockUser.setUserId("1");
        mockUser.setAccount(phone);
        mockUser.setNickname("测试用户");

        when(userAccountApplicationService.getUserByPhone(phone)).thenReturn(Optional.of(mockUser));

        // When & Then
        mockMvc.perform(get("/api/account/users/phone/{phone}", phone))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.account").value(phone))
                .andExpect(jsonPath("$.nickname").value("测试用户"));

        verify(userAccountApplicationService).getUserByPhone(phone);
    }

    @Test
    @DisplayName("GET /api/account/users/phone/{phone} - 用户不存在返回404")
    void getUserByPhone_NotExists_Returns404() throws Exception {
        // Given
        String phone = "13800138000";
        when(userAccountApplicationService.getUserByPhone(phone)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/account/users/phone/{phone}", phone))
                .andExpect(status().isNotFound());

        verify(userAccountApplicationService).getUserByPhone(phone);
    }

    @Test
    @DisplayName("GET /api/account/users/username/{username} - 用户存在返回200")
    void getUserByUsername_Exists_Returns200() throws Exception {
        // Given
        String username = "testuser";
        UserCore mockUser = new UserCore();
        mockUser.setUserId("1");
        mockUser.setAccount(username);
        mockUser.setNickname("测试用户");

        when(userAccountApplicationService.getUserByUsername(username)).thenReturn(Optional.of(mockUser));

        // When & Then
        mockMvc.perform(get("/api/account/users/username/{username}", username))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(1))
                .andExpect(jsonPath("$.account").value(username))
                .andExpect(jsonPath("$.nickname").value("测试用户"));

        verify(userAccountApplicationService).getUserByUsername(username);
    }

    @Test
    @DisplayName("GET /api/account/users/username/{username} - 用户不存在返回404")
    void getUserByUsername_NotExists_Returns404() throws Exception {
        // Given
        String username = "nonexistent";
        when(userAccountApplicationService.getUserByUsername(username)).thenReturn(Optional.empty());

        // When & Then
        mockMvc.perform(get("/api/account/users/username/{username}", username))
                .andExpect(status().isNotFound());

        verify(userAccountApplicationService).getUserByUsername(username);
    }
}

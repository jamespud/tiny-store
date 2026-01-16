package com.github.spud.tinystore.account.application;

import com.github.spud.tinystore.account.domain.event.UserCreatedEvent;
import com.github.spud.tinystore.account.domain.event.UserStatusChangedEvent;
import com.github.spud.tinystore.account.domain.event.UserUpdatedEvent;
import com.github.spud.tinystore.account.infrastructure.event.AccountEventPublisher;
import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.github.spud.tinystore.account.infrastructure.persistence.repository.UserCoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAccountApplicationService 单元测试")
class UserAccountApplicationServiceTest {

    @Mock
    private UserCoreRepository userCoreRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AccountEventPublisher eventPublisher;

    @InjectMocks
    private UserAccountApplicationService service;

    @Test
    @DisplayName("registerUser - 已存在账号应抛出异常")
    void registerUser_AlreadyExists_ThrowsException() {
        // Given
        String phone = "13800138000";
        UserCore existingUser = new UserCore();
        existingUser.setAccount(phone);
        when(userCoreRepository.findByAccount(phone)).thenReturn(Optional.of(existingUser));

        // When & Then
        assertThatThrownBy(() -> service.registerUser(phone, "password", "nickname"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户已存在");

        verify(userCoreRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserCreatedEvent(any());
    }

    @Test
    @DisplayName("registerUser - 成功注册应保存用户并发布事件")
    void registerUser_Success_SavesUserAndPublishesEvent() {
        // Given
        String phone = "13800138000";
        String password = "password123";
        String nickname = "测试用户";
        String encodedPassword = "encoded_password";

        when(userCoreRepository.findByAccount(phone)).thenReturn(Optional.empty());
        when(passwordEncoder.encode(password)).thenReturn(encodedPassword);

        UserCore savedUser = new UserCore();
        savedUser.setUserId(1L);
        savedUser.setAccount(phone);
        savedUser.setPassword(encodedPassword);
        savedUser.setNickname(nickname);
        savedUser.setAccountStatus(1);
        savedUser.setIsDelete(0);
        when(userCoreRepository.save(any(UserCore.class))).thenReturn(savedUser);

        // When
        UserCore result = service.registerUser(phone, password, nickname);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);

        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        UserCore capturedUser = userCaptor.getValue();
        assertThat(capturedUser.getAccount()).isEqualTo(phone);
        assertThat(capturedUser.getPassword()).isEqualTo(encodedPassword);
        assertThat(capturedUser.getNickname()).isEqualTo(nickname);
        assertThat(capturedUser.getAccountStatus()).isEqualTo(1);
        assertThat(capturedUser.getIsDelete()).isEqualTo(0);
        assertThat(capturedUser.getRegisterTime()).isNotNull();

        ArgumentCaptor<UserCreatedEvent> eventCaptor = ArgumentCaptor.forClass(UserCreatedEvent.class);
        verify(eventPublisher).publishUserCreatedEvent(eventCaptor.capture());
        UserCreatedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(1L);
        assertThat(event.getPhone()).isEqualTo(phone);
        assertThat(event.getNickname()).isEqualTo(nickname);
    }

    @Test
    @DisplayName("updateUserProfile - 用户不存在应抛出异常")
    void updateUserProfile_UserNotFound_ThrowsException() {
        // Given
        Long userId = 1L;
        when(userCoreRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updateUserProfile(userId, "new nickname", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");

        verify(userCoreRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserUpdatedEvent(any());
    }

    @Test
    @DisplayName("updateUserProfile - 只更新昵称")
    void updateUserProfile_OnlyNickname_UpdatesNicknameOnly() {
        // Given
        Long userId = 1L;
        UserCore existingUser = new UserCore();
        existingUser.setUserId(userId);
        existingUser.setNickname("old nickname");
        existingUser.setAvatarUrl("old_avatar.jpg");
        existingUser.setExtJson("{\"key\":\"value\"}");

        when(userCoreRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userCoreRepository.save(any(UserCore.class))).thenReturn(existingUser);

        // When
        service.updateUserProfile(userId, "new nickname", null, null);

        // Then
        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        UserCore savedUser = userCaptor.getValue();
        assertThat(savedUser.getNickname()).isEqualTo("new nickname");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("old_avatar.jpg");
        assertThat(savedUser.getExtJson()).isEqualTo("{\"key\":\"value\"}");

        verify(eventPublisher).publishUserUpdatedEvent(any(UserUpdatedEvent.class));
    }

    @Test
    @DisplayName("updateUserProfile - 更新所有字段")
    void updateUserProfile_AllFields_UpdatesAll() {
        // Given
        Long userId = 1L;
        UserCore existingUser = new UserCore();
        existingUser.setUserId(userId);
        existingUser.setNickname("old");
        existingUser.setAvatarUrl("old.jpg");
        existingUser.setExtJson("{}");

        when(userCoreRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(userCoreRepository.save(any(UserCore.class))).thenReturn(existingUser);

        // When
        service.updateUserProfile(userId, "new nickname", "new.jpg", "{\"new\":\"data\"}");

        // Then
        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        UserCore savedUser = userCaptor.getValue();
        assertThat(savedUser.getNickname()).isEqualTo("new nickname");
        assertThat(savedUser.getAvatarUrl()).isEqualTo("new.jpg");
        assertThat(savedUser.getExtJson()).isEqualTo("{\"new\":\"data\"}");

        verify(eventPublisher).publishUserUpdatedEvent(any(UserUpdatedEvent.class));
    }

    @Test
    @DisplayName("resetPassword - 用户不存在应抛出异常")
    void resetPassword_UserNotFound_ThrowsException() {
        // Given
        Long userId = 1L;
        when(userCoreRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.resetPassword(userId, "newPassword"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userCoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPassword - 成功重置密码")
    void resetPassword_Success_EncodesAndSaves() {
        // Given
        Long userId = 1L;
        String newPassword = "newPassword123";
        String encodedPassword = "encoded_new_password";

        UserCore existingUser = new UserCore();
        existingUser.setUserId(userId);
        existingUser.setPassword("old_password");

        when(userCoreRepository.findById(userId)).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.encode(newPassword)).thenReturn(encodedPassword);

        // When
        service.resetPassword(userId, newPassword);

        // Then
        verify(passwordEncoder).encode(newPassword);
        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo(encodedPassword);
    }

    @Test
    @DisplayName("updateUserStatus - 用户不存在应抛出异常")
    void updateUserStatus_UserNotFound_ThrowsException() {
        // Given
        Long userId = 1L;
        when(userCoreRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.updateUserStatus(userId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");

        verify(userCoreRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserStatusChangedEvent(any());
    }

    @Test
    @DisplayName("updateUserStatus - 成功更新状态并发布事件")
    void updateUserStatus_Success_UpdatesAndPublishesEvent() {
        // Given
        Long userId = 1L;
        Integer oldStatus = 1;
        Integer newStatus = 0;

        UserCore existingUser = new UserCore();
        existingUser.setUserId(userId);
        existingUser.setAccountStatus(oldStatus);

        when(userCoreRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // When
        service.updateUserStatus(userId, newStatus);

        // Then
        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getAccountStatus()).isEqualTo(newStatus);

        ArgumentCaptor<UserStatusChangedEvent> eventCaptor = ArgumentCaptor.forClass(UserStatusChangedEvent.class);
        verify(eventPublisher).publishUserStatusChangedEvent(eventCaptor.capture());
        UserStatusChangedEvent event = eventCaptor.getValue();
        assertThat(event.getUserId()).isEqualTo(userId);
        assertThat(event.getOldStatus()).isEqualTo(oldStatus);
        assertThat(event.getNewStatus()).isEqualTo(newStatus);
    }

    @Test
    @DisplayName("deleteUser - 用户不存在应抛出异常")
    void deleteUser_UserNotFound_ThrowsException() {
        // Given
        Long userId = 1L;
        when(userCoreRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> service.deleteUser(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("用户不存在");

        verify(userCoreRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleteUser - 成功删除用户（逻辑删除）")
    void deleteUser_Success_SetsDeleteFlag() {
        // Given
        Long userId = 1L;
        UserCore existingUser = new UserCore();
        existingUser.setUserId(userId);
        existingUser.setIsDelete(0);

        when(userCoreRepository.findById(userId)).thenReturn(Optional.of(existingUser));

        // When
        service.deleteUser(userId);

        // Then
        ArgumentCaptor<UserCore> userCaptor = ArgumentCaptor.forClass(UserCore.class);
        verify(userCoreRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getIsDelete()).isEqualTo(1);
    }
}

package com.github.spud.tinystore.account.interfaces.rest;

import com.github.spud.tinystore.account.application.UserAccountApplicationService;
import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account/users")
@RequiredArgsConstructor
public class UserCommandController {

    private final UserAccountApplicationService userAccountApplicationService;

    @PostMapping("/register")
    public ResponseEntity<UserCore> registerUser(@RequestBody RegisterRequest request) {
        try {
            UserCore userCore = userAccountApplicationService.registerUser(
                    request.phone(),
                    request.password(),
                    request.nickname()
            );
            return ResponseEntity.ok(userCore);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/{userId}/profile")
    public ResponseEntity<UserCore> updateUserProfile(
            @PathVariable Long userId,
            @RequestBody UpdateProfileRequest request) {
        try {
            UserCore userCore = userAccountApplicationService.updateUserProfile(
                    userId,
                    request.nickname(),
                    request.avatarUrl(),
                    request.extJson()
            );
            return ResponseEntity.ok(userCore);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @RequestBody ResetPasswordRequest request) {
        try {
            userAccountApplicationService.resetPassword(userId, request.newPassword());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{userId}/status")
    public ResponseEntity<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody UpdateStatusRequest request) {
        try {
            userAccountApplicationService.updateUserStatus(userId, request.accountStatus());
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        try {
            userAccountApplicationService.deleteUser(userId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Request DTOs
    public record RegisterRequest(String phone, String password, String nickname) {}
    public record UpdateProfileRequest(String nickname, String avatarUrl, String extJson) {}
    public record ResetPasswordRequest(String newPassword) {}
    public record UpdateStatusRequest(Integer accountStatus) {}
}
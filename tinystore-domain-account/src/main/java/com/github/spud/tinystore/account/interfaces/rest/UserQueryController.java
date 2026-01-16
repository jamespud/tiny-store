package com.github.spud.tinystore.account.interfaces.rest;

import com.github.spud.tinystore.account.application.UserAccountApplicationService;
import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/api/account/users")
@RequiredArgsConstructor
public class UserQueryController {

    private final UserAccountApplicationService userAccountApplicationService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserCore> getUserById(@PathVariable Long userId) {
        Optional<UserCore> user = userAccountApplicationService.getUserById(userId);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/phone/{phone}")
    public ResponseEntity<UserCore> getUserByPhone(@PathVariable String phone) {
        Optional<UserCore> user = userAccountApplicationService.getUserByPhone(phone);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/username/{username}")
    public ResponseEntity<UserCore> getUserByUsername(@PathVariable String username) {
        Optional<UserCore> user = userAccountApplicationService.getUserByUsername(username);
        return user.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
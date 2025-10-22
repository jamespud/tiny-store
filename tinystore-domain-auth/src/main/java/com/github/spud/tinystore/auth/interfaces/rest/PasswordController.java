package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.interfaces.dto.request.LoginRequest;
import com.github.spud.tinystore.auth.interfaces.security.password.PasswordAuthenticationToken;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/login")
public class PasswordController {

  private final AuthenticationManager authenticationManager;

  public PasswordController(AuthenticationManager authenticationManager) {
    this.authenticationManager = authenticationManager;
  }

  @PostMapping("/password")
  public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
    Authentication authentication = authenticationManager.authenticate(
        new PasswordAuthenticationToken(loginRequest.principal(), loginRequest.credential()));
    SecurityContextHolder.getContext().setAuthentication(authentication);
    return ResponseEntity.ok().build();
  }
}

package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.domain.service.TokenRevocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/tokens")
public class AdminTokenController {

  private final TokenRevocationService tokenRevocationService;

  public AdminTokenController(TokenRevocationService tokenRevocationService) {
    this.tokenRevocationService = tokenRevocationService;
  }

  @PostMapping("/revoke")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> revoke(@RequestParam("user_id") String userId) {
    tokenRevocationService.revokeAllTokensForUser(userId);
    return ResponseEntity.accepted().build();
  }
}

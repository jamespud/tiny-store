package com.github.spud.tinystore.auth.domain.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService {

  private final JdbcTemplate jdbcTemplate;
  private final UserService userService;

  public TokenRevocationService(JdbcTemplate jdbcTemplate, UserService userService) {
    this.jdbcTemplate = jdbcTemplate;
    this.userService = userService;
  }

  @Transactional
  public void revokeAllTokensForUser(String userId) {
    userService.incrementRtVersion(userId);
    jdbcTemplate.update("delete from oauth2_authorization where principal_name = ?",
        userId.toString());
  }
}

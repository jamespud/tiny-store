package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.RevokeUserTokensCommand;

public interface TokenRevocationUseCase {

  void revokeUserTokens(RevokeUserTokensCommand command);
}
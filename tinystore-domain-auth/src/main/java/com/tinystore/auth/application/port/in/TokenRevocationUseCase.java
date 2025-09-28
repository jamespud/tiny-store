package com.tinystore.auth.application.port.in;

import com.tinystore.auth.application.dto.RevokeUserTokensCommand;

public interface TokenRevocationUseCase {

	void revokeUserTokens(RevokeUserTokensCommand command);
}
package com.github.spud.tinystore.auth.interfaces.security.password;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.service.PasswordApplicationService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Component;

@Component
public class PasswordAuthenticationProvider implements AuthenticationProvider {

	private final PasswordApplicationService passwordApplicationService;

	public PasswordAuthenticationProvider(PasswordApplicationService passwordApplicationService) {
		this.passwordApplicationService = passwordApplicationService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		if (authentication instanceof PasswordAuthenticationToken token) {
			String phone = (String) token.getPrincipal();
			String password = (String) token.getCredentials();
			AuthResult authResult = passwordApplicationService.verifyPassword(new VerifyPasswordCommand(phone, password));
			// TODO: 返回userDetails
			return authResult.user();
		}
		return null;
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return PasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

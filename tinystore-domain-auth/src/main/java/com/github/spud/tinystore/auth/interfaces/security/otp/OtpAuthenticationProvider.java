package com.github.spud.tinystore.auth.interfaces.security.otp;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import com.github.spud.tinystore.auth.interfaces.security.MallUserPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OtpAuthenticationProvider implements AuthenticationProvider {

	private final OtpUseCase otpUseCase;

	public OtpAuthenticationProvider(OtpUseCase otpUseCase) {
		this.otpUseCase = otpUseCase;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		if (!(authentication instanceof OtpAuthenticationToken token)) {
			return null;
		}
		String phone = token.getPhone();
		String code = token.getCode();
		try {
			AuthResult result = otpUseCase.verifyOtp(new VerifyOtpCommand(phone, code));
			MallUserPrincipal principal = new MallUserPrincipal(result.user(), List.of(
				new SimpleGrantedAuthority("ROLE_USER"),
				new SimpleGrantedAuthority("SCOPE_user.profile")
			));
			return new OtpAuthenticationToken(principal, principal.getAuthorities());
		} catch (RuntimeException ex) {
			throw new BadCredentialsException("OTP 验证失败", ex);
		}
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return OtpAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

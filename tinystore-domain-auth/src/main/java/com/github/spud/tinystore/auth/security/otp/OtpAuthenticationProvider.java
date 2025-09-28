package com.github.spud.tinystore.auth.security.otp;

import com.github.spud.tinystore.auth.domain.user.MallUser;
import com.github.spud.tinystore.auth.security.MallUserPrincipal;
import com.github.spud.tinystore.auth.service.OtpService;
import com.github.spud.tinystore.auth.service.UserService;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OtpAuthenticationProvider implements AuthenticationProvider {

	private final OtpService otpService;
	private final UserService userService;

	public OtpAuthenticationProvider(OtpService otpService, UserService userService) {
		this.otpService = otpService;
		this.userService = userService;
	}

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		if (!(authentication instanceof OtpAuthenticationToken token)) {
			return null;
		}
		String phone = token.getPhone();
		String code = token.getCode();
		try {
			otpService.verifyCode(phone, code);
		} catch (RuntimeException ex) {
			throw new BadCredentialsException("OTP 验证失败", ex);
		}
		MallUser user = userService.getOrCreateByPhone(phone);
		MallUserPrincipal principal = new MallUserPrincipal(user, List.of(
			new SimpleGrantedAuthority("ROLE_USER"),
			new SimpleGrantedAuthority("SCOPE_user.profile")
		));
		return new OtpAuthenticationToken(principal, principal.getAuthorities());
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return OtpAuthenticationToken.class.isAssignableFrom(authentication);
	}
}

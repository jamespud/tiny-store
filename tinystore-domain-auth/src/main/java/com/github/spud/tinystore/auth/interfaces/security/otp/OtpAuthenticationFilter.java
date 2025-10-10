package com.github.spud.tinystore.auth.interfaces.security.otp;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.util.StringUtils;

/**
 * 手机验证码登录过滤器
 */
public class OtpAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

	private static final RequestMatcher OTP_MATCHER = request ->
		"POST".equalsIgnoreCase(request.getMethod()) && "/login/otp".equals(request.getServletPath());

	public OtpAuthenticationFilter(AuthenticationManager authenticationManager) {
		super(OTP_MATCHER);
		setAuthenticationManager(authenticationManager);
		setAuthenticationSuccessHandler(new SavedRequestAwareAuthenticationSuccessHandler());
		setAuthenticationFailureHandler(new SimpleUrlAuthenticationFailureHandler("/login?error"));
	}

	@Override
	public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
		throws AuthenticationException {
		String phone = request.getParameter("phone");
		String code = request.getParameter("code");
		if (!StringUtils.hasText(phone) || !StringUtils.hasText(code)) {
			throw new IllegalArgumentException("phone and code must be provided");
		}
		phone = phone.trim();
		code = code.trim();
		OtpAuthenticationToken authRequest = new OtpAuthenticationToken(phone, code);
		return this.getAuthenticationManager().authenticate(authRequest);
	}
}

package com.github.spud.tinystore.auth.application.config;

import com.github.spud.tinystore.auth.interfaces.security.otp.OtpAuthenticationFilter;
import com.github.spud.tinystore.auth.interfaces.security.otp.OtpAuthenticationProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

	private final OtpAuthenticationProvider otpAuthenticationProvider;

	public SecurityConfig(OtpAuthenticationProvider otpAuthenticationProvider) {
		this.otpAuthenticationProvider = otpAuthenticationProvider;

	}

	@Bean
	@Order(2)
	SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, OtpAuthenticationFilter otpAuthenticationFilter) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(
					"/assets/**", "/css/**", "/js/**", "/images/**",
					"/.well-known/**", "/actuator/health", "/error",
					"/api/auth/otp/**", "/login/otp", "/api/auth/login/password"
				).permitAll()
				.anyRequest().authenticated()
			)
			.cors(Customizer.withDefaults())
			.headers(h -> h
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
				.referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
			)
			.formLogin(form ->
				form.loginPage("/login").permitAll()
					.loginProcessingUrl("/login")
			)
			.logout(Customizer.withDefaults())
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/oauth2/**", "/api/auth/otp/**", "/login/otp", "/api/auth/login/password")
			)
			.authenticationProvider(otpAuthenticationProvider)
			.addFilterBefore(otpAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(
		@Qualifier("sysUserDetailsService") UserDetailsService userDetailsService,
		PasswordEncoder passwordEncoder) {

		DaoAuthenticationProvider authenticationProvider = new DaoAuthenticationProvider(userDetailsService);
		authenticationProvider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(otpAuthenticationProvider, authenticationProvider);
	}

	@Bean
	public OtpAuthenticationFilter otpAuthenticationFilter(AuthenticationManager authenticationManager) {
		return new OtpAuthenticationFilter(authenticationManager);
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Value("${tinystore.auth.cors.allowed-origins:http://localhost:3000,http://localhost:8080}")
	private String allowedOrigins;

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration cfg = new CorsConfiguration();
		List<String> origins = Arrays.asList(allowedOrigins.split(","));
		cfg.setAllowedOrigins(origins);
		cfg.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
		cfg.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type"));
		cfg.setExposedHeaders(Arrays.asList("Cache-Control", "Pragma", "Expires"));
		cfg.setAllowCredentials(true);
		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", cfg);
		return source;
	}
}

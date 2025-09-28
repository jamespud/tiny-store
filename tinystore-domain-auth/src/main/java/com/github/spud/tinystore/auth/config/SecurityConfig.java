package com.github.spud.tinystore.auth.config;

import com.github.spud.tinystore.auth.security.otp.OtpAuthenticationFilter;
import com.github.spud.tinystore.auth.security.otp.OtpAuthenticationProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
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
				.requestMatchers("/assets/**", "/css/**", "/js/**", "/images/**",
					"/.well-known/**", "/actuator/health", "/error",
					"/api/auth/otp/**", "/login/otp").permitAll()
				.anyRequest().authenticated()
			)
			.cors(Customizer.withDefaults())
			.headers(h -> h
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
				.referrerPolicy(r -> r.policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
				.frameOptions(f -> f.deny())
			)
			.formLogin(form -> form.loginPage("/login").permitAll())
			.logout(Customizer.withDefaults())
			.csrf(csrf -> csrf
				.ignoringRequestMatchers("/oauth2/**", "/api/auth/otp/**", "/login/otp")
			)
			.authenticationProvider(otpAuthenticationProvider)
			.addFilterBefore(otpAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}

	@Bean
	public OtpAuthenticationFilter otpAuthenticationFilter(AuthenticationManager authenticationManager) {
		return new OtpAuthenticationFilter(authenticationManager);
	}

	// 开发态内存用户；生产请切换为JDBC/外部身份源
	@Bean
	UserDetailsService userDetailsService() {
		var encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
		var user = User.withUsername("user").password(encoder.encode("changeit")).roles("USER").build();
		var admin = User.withUsername("admin").password(encoder.encode("changeit")).roles("ADMIN").build();
		return new InMemoryUserDetailsManager(user, admin);
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

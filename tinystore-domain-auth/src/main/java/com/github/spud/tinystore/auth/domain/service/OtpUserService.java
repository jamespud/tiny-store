package com.github.spud.tinystore.auth.domain.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Service for loading user details for OTP authentication.
 */
public class OtpUserService implements UserDetailsService {

	private StringRedisTemplate redisTemplate;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		// TODO: Implement OTP user loading logic
		throw new UnsupportedOperationException("Not implemented yet");
	}
}

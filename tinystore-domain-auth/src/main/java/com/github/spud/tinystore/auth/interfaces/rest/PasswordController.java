package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.interfaces.dto.request.LoginRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/otp")
public class PasswordController {

	private final PasswordUserCase passwordUserCase;
	private final AuthenticationManager authenticationManager;

	public PasswordController(PasswordUserCase passwordUserCase, AuthenticationManager authenticationManager) {
		this.passwordUserCase = passwordUserCase;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/login")
	public ResponseEntity<Void> login(@RequestBody LoginRequest loginRequest) {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}

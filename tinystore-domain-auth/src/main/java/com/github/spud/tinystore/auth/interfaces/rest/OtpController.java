package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.security.otp.OtpAuthenticationToken;
import com.github.spud.tinystore.auth.service.OtpService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

	private final OtpService otpService;
	private final AuthenticationManager authenticationManager;

	public OtpController(OtpService otpService, AuthenticationManager authenticationManager) {
		this.otpService = otpService;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/send")
	public ResponseEntity<Void> send(@Valid @RequestBody SendRequest request) {
		otpService.sendCode(request.phone());
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/verify")
	public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest request) {
		Authentication authentication = authenticationManager.authenticate(
			new OtpAuthenticationToken(request.phone(), request.code()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return ResponseEntity.ok().build();
	}

	public record SendRequest(@NotBlank String phone) {
	}

	public record VerifyRequest(@NotBlank String phone, @NotBlank String code) {
	}
}

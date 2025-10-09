package com.github.spud.tinystore.auth.interfaces.rest;

import com.github.spud.tinystore.auth.application.dto.SendOtpCommand;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import com.github.spud.tinystore.auth.interfaces.security.otp.OtpAuthenticationToken;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/otp")
public class OtpController {

	private final OtpUseCase otpUseCase;
	private final AuthenticationManager authenticationManager;

	public OtpController(OtpUseCase otpUseCase, AuthenticationManager authenticationManager) {
		this.otpUseCase = otpUseCase;
		this.authenticationManager = authenticationManager;
	}

	@PostMapping("/send")
	public ResponseEntity<Void> send(@Valid @RequestBody SendRequest request, HttpServletRequest servletRequest) {
		otpUseCase.sendOtp(new SendOtpCommand(
			request.phone(),
			request.requestId() != null ? request.requestId() : UUID.randomUUID().toString(),
			servletRequest.getRemoteAddr(),
			servletRequest.getHeader("User-Agent")));
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/verify")
	public ResponseEntity<Void> verify(@Valid @RequestBody VerifyRequest request) {
		Authentication authentication = authenticationManager.authenticate(
			new OtpAuthenticationToken(request.phone(), request.code()));
		SecurityContextHolder.getContext().setAuthentication(authentication);
		return ResponseEntity.ok().build();
	}

	public record SendRequest(@NotBlank String phone, String requestId) {
	}

	public record VerifyRequest(@NotBlank String phone, @NotBlank String code) {
	}
}
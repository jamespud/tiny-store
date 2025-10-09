package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;
import com.github.spud.tinystore.auth.application.port.in.AuthUseCase;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import org.springframework.stereotype.Service;

@Service
public class AuthApplicationService implements AuthUseCase {

	private final OtpUseCase otpUseCase;

	public AuthApplicationService(OtpUseCase otpUseCase) {
		this.otpUseCase = otpUseCase;
	}

	@Override
	public AuthResult loginByOtp(VerifyOtpCommand command) {
		return otpUseCase.verifyOtp(command);
	}
}
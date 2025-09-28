package com.tinystore.auth.application.service;

import org.springframework.stereotype.Service;

import com.tinystore.auth.application.dto.AuthResult;
import com.tinystore.auth.application.dto.VerifyOtpCommand;
import com.tinystore.auth.application.port.in.AuthUseCase;
import com.tinystore.auth.application.port.in.OtpUseCase;

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
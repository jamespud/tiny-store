package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.SendOtpCommand;
import com.github.spud.tinystore.auth.application.dto.SendOtpResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;

public interface OtpUseCase {

	SendOtpResult sendOtp(SendOtpCommand command);

	AuthResult verifyOtp(VerifyOtpCommand command);
}
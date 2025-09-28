package com.tinystore.auth.application.port.in;

import com.tinystore.auth.application.dto.AuthResult;
import com.tinystore.auth.application.dto.SendOtpCommand;
import com.tinystore.auth.application.dto.SendOtpResult;
import com.tinystore.auth.application.dto.VerifyOtpCommand;

public interface OtpUseCase {

	SendOtpResult sendOtp(SendOtpCommand command);

	AuthResult verifyOtp(VerifyOtpCommand command);
}
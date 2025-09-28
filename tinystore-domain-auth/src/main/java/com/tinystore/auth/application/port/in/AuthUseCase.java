package com.tinystore.auth.application.port.in;

import com.tinystore.auth.application.dto.AuthResult;
import com.tinystore.auth.application.dto.VerifyOtpCommand;

public interface AuthUseCase {

	AuthResult loginByOtp(VerifyOtpCommand command);
}
package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyOtpCommand;

public interface AuthUseCase {

  AuthResult loginByOtp(VerifyOtpCommand command);

  AuthResult loginByPassword(VerifyOtpCommand command);

}

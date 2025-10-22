package com.github.spud.tinystore.auth.application.port.in;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;

public interface PasswordUserCase {

  AuthResult verifyPassword(VerifyPasswordCommand command);
}

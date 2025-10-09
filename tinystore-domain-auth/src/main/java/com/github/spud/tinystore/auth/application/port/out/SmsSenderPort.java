package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;

public interface SmsSenderPort {

	OtpCode sendLoginCode(PhoneNumber phone, OtpCode code);
}
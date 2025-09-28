package com.tinystore.auth.application.port.out;

import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;

public interface SmsSenderPort {

	OtpCode sendLoginCode(PhoneNumber phone, OtpCode code);
}
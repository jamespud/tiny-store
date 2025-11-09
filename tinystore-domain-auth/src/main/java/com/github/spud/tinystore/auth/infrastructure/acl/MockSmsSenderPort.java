package com.github.spud.tinystore.auth.infrastructure.acl;

import com.github.spud.tinystore.auth.application.port.out.SmsSenderPort;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import org.springframework.stereotype.Component;

@Component
public class MockSmsSenderPort implements SmsSenderPort {

	@Override
	public OtpCode sendLoginCode(PhoneNumber phone, OtpCode code) {
		return null;
	}
}

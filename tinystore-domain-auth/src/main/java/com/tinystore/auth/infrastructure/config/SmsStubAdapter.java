package com.tinystore.auth.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import com.tinystore.auth.application.port.out.SmsSenderPort;
import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;

@Component
@Primary
public class SmsStubAdapter implements SmsSenderPort {

	private static final Logger log = LoggerFactory.getLogger(SmsStubAdapter.class);
	private static final OtpCode FIXED = OtpCode.of("123456");

	@Override
	public OtpCode sendLoginCode(PhoneNumber phone, OtpCode code) {
		log.info("[SMS-STUB] send login code {} (generated: {}) to {}", FIXED.getValue(), code.getValue(), phone.getValue());
		return FIXED;
	}
}
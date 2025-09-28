package com.github.spud.tinystore.auth.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SmsStubClient {

	private static final Logger log = LoggerFactory.getLogger(SmsStubClient.class);
	private static final String FIXED_CODE = "123456";

	public String sendLoginCode(String phone) {
		log.info("[SMS-STUB] send login code {} to {}", FIXED_CODE, phone);
		return FIXED_CODE;
	}
}

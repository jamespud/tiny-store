package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.dto.AuthResult;
import com.github.spud.tinystore.auth.application.dto.VerifyPasswordCommand;
import com.github.spud.tinystore.auth.application.port.in.PasswordUserCase;
import com.github.spud.tinystore.auth.application.port.out.AuditLogPort;
import com.github.spud.tinystore.auth.application.port.out.SmsSenderPort;
import com.github.spud.tinystore.auth.application.port.out.UserRepository;
import com.github.spud.tinystore.auth.domain.service.OtpGenerationService;
import org.springframework.stereotype.Service;

@Service
public class PasswordApplicationService implements PasswordUserCase {

	private final UserRepository userRepository;
	private final SmsSenderPort smsSenderPort;
	private final OtpGenerationService otpGenerationService;
	private final AuditLogPort auditLogPort;

	public PasswordApplicationService(UserRepository userRepository, SmsSenderPort smsSenderPort, OtpGenerationService otpGenerationService, AuditLogPort auditLogPort) {
		this.userRepository = userRepository;
		this.smsSenderPort = smsSenderPort;
		this.otpGenerationService = otpGenerationService;
		this.auditLogPort = auditLogPort;
	}


	@Override
	public AuthResult verifyPassword(VerifyPasswordCommand command) {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}

package com.tinystore.auth.application.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinystore.auth.application.dto.AuthResult;
import com.tinystore.auth.application.dto.MallUserView;
import com.tinystore.auth.application.dto.SendOtpCommand;
import com.tinystore.auth.application.dto.SendOtpResult;
import com.tinystore.auth.application.dto.VerifyOtpCommand;
import com.tinystore.auth.application.port.in.OtpUseCase;
import com.tinystore.auth.application.port.out.AuditLogPort;
import com.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.tinystore.auth.application.port.out.SmsSenderPort;
import com.tinystore.auth.application.port.out.UserRepositoryPort;
import com.tinystore.auth.domain.audit.AuditEvent;
import com.tinystore.auth.domain.exception.OtpInvalidException;
import com.tinystore.auth.domain.model.otp.Otp;
import com.tinystore.auth.domain.model.user.MallUser;
import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;
import com.tinystore.auth.domain.service.OtpGenerationService;

@Service
public class OtpApplicationService implements OtpUseCase {

	private static final String ACTION_OTP_SEND = "OTP_SEND";
	private static final String ACTION_OTP_VERIFY = "OTP_VERIFY";
	private static final String ACTION_USER_REGISTER = "USER_REGISTER";

	private final UserRepositoryPort userRepository;
	private final OtpRepositoryPort otpRepository;
	private final SmsSenderPort smsSenderPort;
	private final OtpGenerationService otpGenerationService;
	private final AuditLogPort auditLogPort;

	public OtpApplicationService(UserRepositoryPort userRepository,
	                            OtpRepositoryPort otpRepository,
	                            SmsSenderPort smsSenderPort,
	                            OtpGenerationService otpGenerationService,
	                            AuditLogPort auditLogPort) {
		this.userRepository = userRepository;
		this.otpRepository = otpRepository;
		this.smsSenderPort = smsSenderPort;
		this.otpGenerationService = otpGenerationService;
		this.auditLogPort = auditLogPort;
	}

	@Override
	@Transactional
	public SendOtpResult sendOtp(SendOtpCommand command) {
		PhoneNumber phone = PhoneNumber.of(command.phone());
		Otp otp = otpGenerationService.generate(phone);
		var deliveredCode = smsSenderPort.sendLoginCode(phone, otp.getCode());
		Otp toPersist = deliveredCode.equals(otp.getCode())
			? otp
			: new Otp(otp.getId(), phone, deliveredCode, otp.getExpireAt(), false, null);
		otpRepository.save(toPersist);
		auditLogPort.append(AuditEvent.success(null, phone.getValue(), null, ACTION_OTP_SEND, Set.of(), command.ip(), command.userAgent(),
			command.requestId() != null ? "requestId=" + command.requestId() : null));
		return new SendOtpResult(phone.masked());
	}

	@Override
	@Transactional
	public AuthResult verifyOtp(VerifyOtpCommand command) {
		PhoneNumber phone = PhoneNumber.of(command.phone());
		OtpCode code = OtpCode.of(command.code());
		Otp otp = otpRepository.findLatest(phone)
			.orElseThrow(() -> {
				auditLogPort.append(AuditEvent.failure(null, phone.getValue(), null, ACTION_OTP_VERIFY, Set.of(), null, null, "otp_not_found"));
				return new OtpInvalidException("验证码不存在或已失效");
			});
		otpGenerationService.verify(otp, code);
		otpRepository.markUsed(otp);
		Optional<MallUser> existing = userRepository.findByPhone(phone);
		MallUser user = existing.orElseGet(() -> {
			MallUser created = userRepository.save(createUser(phone));
			auditLogPort.append(AuditEvent.success(created.getId().getValue(), created.getPhone().getValue(), null, ACTION_USER_REGISTER, Set.of(), null, null, "auto_register"));
			return created;
		});
		try {
			user.ensureActive();
		} catch (RuntimeException ex) {
			auditLogPort.append(AuditEvent.failure(user.getId().getValue(), user.getPhone().getValue(), null, ACTION_OTP_VERIFY, Set.of(), null, null, ex.getMessage()));
			throw ex;
		}

		auditLogPort.append(AuditEvent.success(user.getId().getValue(), user.getPhone().getValue(), null, ACTION_OTP_VERIFY, Set.of(), null, null, existing.isPresent() ? "existing_user" : "new_user"));
		return new AuthResult(mapToView(user));
	}

	private MallUser createUser(PhoneNumber phone) {
		String phoneValue = phone.getValue();
		String nickname = "用户" + phoneValue.substring(Math.max(phoneValue.length() - 4, 0));
		return MallUser.register(UserId.random(), phone, nickname, null, OffsetDateTime.now());
	}

	private MallUserView mapToView(MallUser user) {
		return new MallUserView(
			user.getId().getValue(),
			user.getPhone().getValue(),
			user.getNickname(),
			user.getAvatar(),
			user.getStatus(),
			user.getRtVersion().getValue(),
			user.getCreatedAt(),
			user.getUpdatedAt());
	}
}
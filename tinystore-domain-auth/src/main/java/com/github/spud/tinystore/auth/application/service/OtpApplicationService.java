package com.github.spud.tinystore.auth.application.service;

import com.github.spud.tinystore.auth.application.config.OtpProperties;
import com.github.spud.tinystore.auth.application.dto.*;
import com.github.spud.tinystore.auth.application.port.in.OtpUseCase;
import com.github.spud.tinystore.auth.application.port.out.*;
import com.github.spud.tinystore.auth.domain.audit.AuditEvent;
import com.github.spud.tinystore.auth.domain.exception.OtpInvalidException;
import com.github.spud.tinystore.auth.domain.exception.OtpRateLimitExceededException;
import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.model.user.MallUser;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import com.github.spud.tinystore.auth.domain.service.OtpGenerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

@Service
public class OtpApplicationService implements OtpUseCase {

	private static final String ACTION_OTP_SEND = "OTP_SEND";
	private static final String ACTION_OTP_VERIFY = "OTP_VERIFY";
	private static final String ACTION_USER_REGISTER = "USER_REGISTER";
	private static final String OTP_LOCK_KEY_PREFIX = "auth:otp:lock:";
	private static final String OTP_COUNTER_KEY_PREFIX = "auth:otp:count:";
	private static final String OTP_BLOCK_KEY_PREFIX = "auth:otp:block:";
	private static final String OTP_REQUEST_KEY_PREFIX = "auth:otp:req:";

	private final UserRepository userRepository;
	private final OtpRepositoryPort otpRepository;
	private final SmsSenderPort smsSenderPort;
	private final OtpGenerationService otpGenerationService;
	private final AuditLogPort auditLogPort;
	private final LockAndRateLimitPort lockAndRateLimitPort;
	private final OtpProperties otpProperties;

	public OtpApplicationService(UserRepository userRepository,
	                             OtpRepositoryPort otpRepository,
	                             SmsSenderPort smsSenderPort,
	                             OtpGenerationService otpGenerationService,
	                             AuditLogPort auditLogPort,
	                             LockAndRateLimitPort lockAndRateLimitPort,
	                             OtpProperties otpProperties) {
		this.userRepository = userRepository;
		this.otpRepository = otpRepository;
		this.smsSenderPort = smsSenderPort;
		this.otpGenerationService = otpGenerationService;
		this.auditLogPort = auditLogPort;
		this.lockAndRateLimitPort = lockAndRateLimitPort;
		this.otpProperties = otpProperties;
	}

	@Override
	@Transactional
	public SendOtpResult sendOtp(SendOtpCommand command) {
		PhoneNumber phone = PhoneNumber.of(command.phone());
		try {
			return lockAndRateLimitPort.withLock(lockKey(phone), otpProperties.getRequestLockTtl(),
				() -> sendOtpWithThrottle(command, phone));
		} catch (OtpRateLimitExceededException ex) {
			auditLogPort.append(AuditEvent.failure(null, phone.getValue(), null, ACTION_OTP_SEND, Set.of(), command.ip(), command.userAgent(), ex.getMessage()));
			throw ex;
		}
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

	private SendOtpResult sendOtpWithThrottle(SendOtpCommand command, PhoneNumber phone) {
		String phoneValue = phone.getValue();
		if (isBlocked(phoneValue)) {
			throw new OtpRateLimitExceededException("otp_blocked");
		}
		if (command.requestId() != null) {
			String requestKey = requestKey(command.requestId());
			if (lockAndRateLimitPort.get(requestKey) != null) {
				auditLogPort.append(AuditEvent.success(null, phone.getValue(), null, ACTION_OTP_SEND, Set.of(), command.ip(), command.userAgent(),
					"idempotent_hit"));
				return new SendOtpResult(phone.masked());
			}
		}
		long sendCount = lockAndRateLimitPort.increment(counterKey(phoneValue), otpProperties.getRateWindow());
		if (sendCount > otpProperties.getMaxSendPerWindow()) {
			lockAndRateLimitPort.setIfAbsent(blockKey(phoneValue), "rate_limited", otpProperties.getBlockDuration());
			throw new OtpRateLimitExceededException("otp_rate_limit_exceeded");
		}
		Otp otp = otpGenerationService.generate(phone);
		var deliveredCode = smsSenderPort.sendLoginCode(phone, otp.getCode());
		Otp toPersist = deliveredCode.equals(otp.getCode())
			? otp
			: new Otp(otp.getId(), phone, deliveredCode, otp.getExpireAt(), false, null);
		otpRepository.save(toPersist);
		if (command.requestId() != null) {
			lockAndRateLimitPort.setIfAbsent(requestKey(command.requestId()), phone.masked(), otpProperties.getRequestCacheTtl());
		}
		auditLogPort.append(AuditEvent.success(null, phone.getValue(), null, ACTION_OTP_SEND, Set.of(), command.ip(), command.userAgent(),
			command.requestId() != null ? "requestId=" + command.requestId() : null));
		return new SendOtpResult(phone.masked());
	}

	private boolean isBlocked(String phoneValue) {
		return lockAndRateLimitPort.get(blockKey(phoneValue)) != null;
	}

	private String lockKey(PhoneNumber phone) {
		return OTP_LOCK_KEY_PREFIX + phone.getValue();
	}

	private String requestKey(String requestId) {
		return OTP_REQUEST_KEY_PREFIX + requestId;
	}

	private String blockKey(String phoneValue) {
		return OTP_BLOCK_KEY_PREFIX + phoneValue;
	}

	private String counterKey(String phoneValue) {
		long windowSeconds = Math.max(otpProperties.getRateWindow().getSeconds(), 1);
		long bucket = OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).toEpochSecond() / windowSeconds;
		return OTP_COUNTER_KEY_PREFIX + phoneValue + ":" + bucket;
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
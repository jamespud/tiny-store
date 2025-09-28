package com.tinystore.auth.application.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tinystore.auth.application.dto.AuthResult;
import com.tinystore.auth.application.dto.MallUserView;
import com.tinystore.auth.application.dto.SendOtpCommand;
import com.tinystore.auth.application.dto.SendOtpResult;
import com.tinystore.auth.application.dto.VerifyOtpCommand;
import com.tinystore.auth.application.port.in.OtpUseCase;
import com.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.tinystore.auth.application.port.out.SmsSenderPort;
import com.tinystore.auth.application.port.out.UserRepositoryPort;
import com.tinystore.auth.domain.exception.OtpInvalidException;
import com.tinystore.auth.domain.model.otp.Otp;
import com.tinystore.auth.domain.model.user.MallUser;
import com.tinystore.auth.domain.primitives.OtpCode;
import com.tinystore.auth.domain.primitives.PhoneNumber;
import com.tinystore.auth.domain.primitives.UserId;
import com.tinystore.auth.domain.service.OtpGenerationService;

@Service
public class OtpApplicationService implements OtpUseCase {

	private final UserRepositoryPort userRepository;
	private final OtpRepositoryPort otpRepository;
	private final SmsSenderPort smsSenderPort;
	private final OtpGenerationService otpGenerationService;

	public OtpApplicationService(UserRepositoryPort userRepository,
	                            OtpRepositoryPort otpRepository,
	                            SmsSenderPort smsSenderPort,
	                            OtpGenerationService otpGenerationService) {
		this.userRepository = userRepository;
		this.otpRepository = otpRepository;
		this.smsSenderPort = smsSenderPort;
		this.otpGenerationService = otpGenerationService;
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
		return new SendOtpResult(phone.masked());
	}

	@Override
	@Transactional
	public AuthResult verifyOtp(VerifyOtpCommand command) {
		PhoneNumber phone = PhoneNumber.of(command.phone());
		OtpCode code = OtpCode.of(command.code());
		Otp otp = otpRepository.findLatest(phone)
			.orElseThrow(() -> new OtpInvalidException("验证码不存在或已失效"));
		otpGenerationService.verify(otp, code);
		otpRepository.markUsed(otp);
		MallUser user = userRepository.findByPhone(phone)
			.orElseGet(() -> userRepository.save(createUser(phone)));
		user.ensureActive();
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
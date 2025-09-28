package com.tinystore.auth.application.port.out;

import java.util.Optional;

import com.tinystore.auth.domain.model.otp.Otp;
import com.tinystore.auth.domain.primitives.PhoneNumber;

public interface OtpRepositoryPort {

	Otp save(Otp otp);

	Optional<Otp> findLatest(PhoneNumber phone);

	void markUsed(Otp otp);
}
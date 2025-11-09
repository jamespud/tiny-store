package com.github.spud.tinystore.auth.infrastructure.port;

import com.github.spud.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOtpRepositoryPort implements OtpRepositoryPort {

	@Override
	public Otp save(Otp otp) {
		return null;
	}

	@Override
	public Optional<Otp> findLatest(PhoneNumber phone) {
		return Optional.empty();
	}

	@Override
	public void markUsed(Otp otp) {

	}
}

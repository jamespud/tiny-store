package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Optional;
import org.springframework.stereotype.Repository;

public interface OtpRepositoryPort {

  Otp save(Otp otp);

  Optional<Otp> findLatest(PhoneNumber phone);

  void markUsed(Otp otp);
}
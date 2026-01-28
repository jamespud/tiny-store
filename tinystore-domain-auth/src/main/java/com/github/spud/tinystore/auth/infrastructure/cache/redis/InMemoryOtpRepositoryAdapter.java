package com.github.spud.tinystore.auth.infrastructure.cache.redis;

import com.github.spud.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 开发态内存 OTP 仓储适配器，便于无 Redis 情况下运行。
 */
@Component
@Primary
public class InMemoryOtpRepositoryAdapter implements OtpRepositoryPort {

  private final Map<String, Otp> store = new ConcurrentHashMap<>();

  @Override
  public Otp save(Otp otp) {
    store.put(otp.getPhone().value(), otp);
    return otp;
  }

  @Override
  public Optional<Otp> findLatest(PhoneNumber phone) {
    return Optional.ofNullable(store.get(phone.value()));
  }

  @Override
  public void markUsed(Otp otp) {
    Otp latest = store.get(otp.getPhone().value());
    if (latest != null) {
      latest.markUsed();
      store.put(latest.getPhone().value(), latest);
    }
  }
}
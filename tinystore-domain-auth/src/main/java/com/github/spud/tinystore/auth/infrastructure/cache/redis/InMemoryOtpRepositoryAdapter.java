package com.github.spud.tinystore.auth.infrastructure.cache.redis;

import com.github.spud.tinystore.auth.application.port.out.OtpRepositoryPort;
import com.github.spud.tinystore.auth.domain.model.otp.Otp;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import com.github.spud.tinystore.auth.application.port.out.OtpConsumeResult;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import java.time.OffsetDateTime;

/**
 * 本地/测试态内存 OTP 仓储适配器。
 *
 * <p>只允许在 {@code local} / {@code test} profile 生效（Ruling R6）：
 * 内存实现天然是"每个 JVM 一份"，多副本部署下会出现"验证码只在签发它的副本里存在"（C4）。
 * 因此 compose/dev/prod 一律使用 {@link RedisOtpRepositoryAdapter}。
 */
@Component
@Primary
@Profile({"local", "test"})
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

  @Override
  public OtpConsumeResult verifyAndConsume(PhoneNumber phone, OtpCode code) {
    AtomicReference<OtpConsumeResult> outcome =
      new AtomicReference<>(OtpConsumeResult.NOT_FOUND);
    // ConcurrentHashMap.compute 对该 key 是原子的：比对与消费不可被并发插入。
    store.compute(phone.value(), (key, current) -> {
      if (current == null) {
        outcome.set(OtpConsumeResult.NOT_FOUND);
        return null;
      }
      if (current.getExpireAt().isBefore(OffsetDateTime.now())) {
        outcome.set(OtpConsumeResult.EXPIRED);
        return null;
      }
      if (!current.getCode().equals(code)) {
        outcome.set(OtpConsumeResult.MISMATCH);
        return current;
      }
      outcome.set(OtpConsumeResult.SUCCESS);
      return null; // 消费：移除记录，重复使用将得到 NOT_FOUND
    });
    return outcome.get();
  }
}

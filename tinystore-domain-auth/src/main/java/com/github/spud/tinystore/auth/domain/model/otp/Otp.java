package com.github.spud.tinystore.auth.domain.model.otp;

import com.github.spud.tinystore.auth.domain.exception.OtpExpiredException;
import com.github.spud.tinystore.auth.domain.exception.OtpInvalidException;
import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Otp {

  private final String id;
  private final PhoneNumber phone;
  private final OtpCode code;
  private final OffsetDateTime expireAt;
  private boolean used;
  private OffsetDateTime usedAt;

  public Otp(String id, PhoneNumber phone, OtpCode code, OffsetDateTime expireAt, boolean used,
      OffsetDateTime usedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.phone = Objects.requireNonNull(phone, "phone");
    this.code = Objects.requireNonNull(code, "code");
    this.expireAt = Objects.requireNonNull(expireAt, "expireAt");
    this.used = used;
    this.usedAt = usedAt;
  }

  public static Otp create(PhoneNumber phone, OtpCode code, OffsetDateTime expireAt) {
    return new Otp(UUID.randomUUID().toString().replace("-", ""), phone, code, expireAt, false,
        null);
  }

  public void ensureValid(OtpCode input) {
    if (used) {
      throw new OtpInvalidException("otp already used");
    }
    if (expireAt.isBefore(OffsetDateTime.now())) {
      throw new OtpExpiredException("otp expired");
    }
    if (!code.equals(input)) {
      throw new OtpInvalidException("otp mismatch");
    }
  }

  public void markUsed() {
    this.used = true;
    this.usedAt = OffsetDateTime.now();
  }

}
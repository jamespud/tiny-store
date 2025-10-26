package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.primitives.OtpCode;
import com.github.spud.tinystore.auth.domain.primitives.PhoneNumber;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;

public interface SmsSenderPort {

  OtpCode sendLoginCode(PhoneNumber phone, OtpCode code);
}
package com.github.spud.tinystore.auth.infrastructure.feign.fallback;

import com.github.spud.tinystore.auth.infrastructure.feign.AccountServiceFeignClient;
import feign.hystrix.FallbackFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccountServiceFallbackFactory implements FallbackFactory<AccountServiceFeignClient> {

    @Override
    public AccountServiceFeignClient create(Throwable cause) {
        log.error("Account service call failed: {}", cause.getMessage(), cause);
        return new AccountServiceFeignClient() {
            @Override
            public ResponseEntity<UserCoreDto> getUserById(Long userId) {
                return ResponseEntity.notFound().build();
            }

            @Override
            public ResponseEntity<UserCoreDto> getUserByPhone(String phone) {
                return ResponseEntity.notFound().build();
            }

            @Override
            public ResponseEntity<UserCoreDto> getUserByUsername(String username) {
                return ResponseEntity.notFound().build();
            }
        };
    }
}
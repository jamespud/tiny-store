package com.github.spud.tinystore.account.application;

import com.github.spud.tinystore.account.infrastructure.persistence.entity.UserCore;
import com.github.spud.tinystore.account.infrastructure.persistence.repository.UserCoreRepository;
import com.github.spud.tinystore.account.interfaces.dto.internal.CredentialVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CredentialVerifyApplicationService {

    private final UserCoreRepository userCoreRepository;
    private final PasswordEncoder passwordEncoder;

    public CredentialVerifyResponse verify(String phone, String password) {
        UserCore user = userCoreRepository.findByAccount(phone)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }
        if (user.getAccountStatus() == null || user.getAccountStatus() != 1) {
            throw new UserDisabledException();
        }
        Long version = user.getCredentialVersion() == null ? 1L : user.getCredentialVersion();
        return new CredentialVerifyResponse(
                user.getUserId(),
                user.getAccount(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getAccountStatus(),
                version
        );
    }
}

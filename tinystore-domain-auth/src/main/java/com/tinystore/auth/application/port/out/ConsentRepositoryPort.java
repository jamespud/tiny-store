package com.tinystore.auth.application.port.out;

import java.util.Optional;

import com.tinystore.auth.domain.model.consent.UserConsent;
import com.tinystore.auth.domain.primitives.ClientId;
import com.tinystore.auth.domain.primitives.UserId;

public interface ConsentRepositoryPort {

	Optional<UserConsent> find(UserId userId, ClientId clientId);

	UserConsent save(UserConsent consent);
}
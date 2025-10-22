package com.github.spud.tinystore.auth.application.port.out;

import com.github.spud.tinystore.auth.domain.model.consent.UserConsent;
import com.github.spud.tinystore.auth.domain.primitives.ClientId;
import com.github.spud.tinystore.auth.domain.primitives.UserId;
import java.util.Optional;

public interface ConsentRepositoryPort {

  Optional<UserConsent> find(UserId userId, ClientId clientId);

  UserConsent save(UserConsent consent);
}
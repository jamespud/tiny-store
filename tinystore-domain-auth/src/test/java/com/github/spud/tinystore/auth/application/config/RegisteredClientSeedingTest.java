package com.github.spud.tinystore.auth.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Regression for the multi-replica startup race in {@link RegisteredClientConfig#initClients}.
 *
 * <p>The seed used to be check-then-insert: with N auth replicas booting at once, two could both see
 * "absent" and both INSERT, and the loser died with
 * {@code duplicate key value violates unique constraint "uk_oauth2_registered_client_client_id"} —
 * i.e. {@code Application run failed} and that replica gone. That is what stopped
 * {@code make e2e-multi MULTI_REPLICAS=3} from ever reaching N healthy auth instances.
 *
 * <p>The real proof is the 3-replica stack run (the topology gate asserts N healthy auth instances);
 * these tests pin the two properties that make it work: a stable derived id, and a lost race being
 * treated as success rather than a fatal error.
 */
@DisplayName("auth registered-client seeding (multi-replica safety)")
class RegisteredClientSeedingTest {

    private static final String[] CLIENT_IDS =
        {"tinystore-web", "tinystore-internal", "tinystore-tool"};

    @Test
    @DisplayName("seeded client id is derived, so every replica computes the same identity")
    void seededClientId_isDeterministic() {
        assertThat(RegisteredClientConfig.seededClientId("tinystore-web"))
            .isEqualTo(RegisteredClientConfig.seededClientId("tinystore-web"))
            .isNotEqualTo(RegisteredClientConfig.seededClientId("tinystore-tool"));
    }

    @Test
    @DisplayName("an already-seeded client is never re-inserted")
    void seeding_existingClients_doesNotSave() throws Exception {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        when(repository.findByClientId(anyString()))
            .thenAnswer(invocation -> registeredClient(invocation.getArgument(0)));

        runner(repository).run();

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("losing the insert race is not fatal: the row seeded by another replica is accepted")
    void seeding_lostInsertRace_isNotFatal() throws Exception {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        // Only "tinystore-web" is missing: first read absent (so we try to insert), that insert loses
        // the race, the re-read then finds the row another replica just created.
        AtomicInteger webReads = new AtomicInteger();
        when(repository.findByClientId("tinystore-web")).thenAnswer(invocation ->
            webReads.incrementAndGet() == 1 ? null : registeredClient("tinystore-web"));
        when(repository.findByClientId("tinystore-internal"))
            .thenReturn(registeredClient("tinystore-internal"));
        when(repository.findByClientId("tinystore-tool"))
            .thenReturn(registeredClient("tinystore-tool"));
        doThrow(new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"uk_oauth2_registered_client_client_id\""))
            .when(repository).save(any());

        assertThatCode(() -> runner(repository).run()).doesNotThrowAnyException();

        verify(repository, times(1)).save(any());
    }

    @Test
    @DisplayName("a genuine insert failure (row still absent) is still surfaced")
    void seeding_realFailure_propagates() {
        RegisteredClientRepository repository = mock(RegisteredClientRepository.class);
        when(repository.findByClientId(anyString())).thenReturn(null);
        doThrow(new DataIntegrityViolationException("boom")).when(repository).save(any());

        assertThatCode(() -> runner(repository).run())
            .isInstanceOf(DataIntegrityViolationException.class)
            .hasMessageContaining("boom");
    }

    /**
     * Builds the real CommandLineRunner bean (its {@code @Value} TTL fields are normally injected by
     * Spring, so set them here) backed by the stubbed repository.
     */
    private CommandLineRunner runner(RegisteredClientRepository repository) {
        RegisteredClientConfig config = new RegisteredClientConfig();
        ReflectionTestUtils.setField(config, "userAccessTokenTtl", Duration.ofHours(2));
        ReflectionTestUtils.setField(config, "userRefreshTokenTtl", Duration.ofDays(30));
        ReflectionTestUtils.setField(config, "userIdTokenTtl", Duration.ofHours(2));
        ReflectionTestUtils.setField(config, "m2mAccessTokenTtl", Duration.ofMinutes(5));
        return config.initClients(repository, new BCryptPasswordEncoder());
    }

    /** A stored client, as another replica would have left it. */
    private static RegisteredClient registeredClient(String clientId) {
        return RegisteredClient.withId("existing-" + clientId)
            .clientId(clientId)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .build();
    }
}

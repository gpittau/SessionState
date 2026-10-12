package com.arquitectura.session;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionIntegrationTest {
    private static HazelcastInstance hazelcast;
    private UserSessionManager manager;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("session-integration-tests");
        config.setProperty("hazelcast.partition.count", "7");
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        hazelcast = Hazelcast.newHazelcastInstance(config);
    }

    @AfterAll
    static void shutdownHazelcast() {
        if (hazelcast != null) {
            hazelcast.shutdown();
        }
    }

    @BeforeEach
    void setupManager() {
        hazelcast.getMap("testMap").clear();
        hazelcast.getMultiMap("testIdx").clear();

        ExpirationManager expiration = new ExpirationManager(
                new SessionExpirationPolicy() {
                    @Override
                    public Duration getIdleTimeout() {
                        return Duration.ofHours(1);
                    }

                    @Override
                    public Optional<Duration> getAbsoluteTimeout() {
                        return Optional.empty();
                    }
                });

        SessionBackendSPI rawBackend =
                new LegacyHazelcastBackend(hazelcast, "testMap", "testIdx");
        SessionBackendSPI observableBackend =
                new ObservableSessionBackendSPI(rawBackend, OpenTelemetry.noop());
        GenericSessionManager coreManager = new GenericSessionManager(
                observableBackend,
                expiration,
                UserSessionConcurrencyPolicy.MULTIPLE);
        manager = new ObservableSessionManager(coreManager, OpenTelemetry.noop());
    }

    @Test
    void createsAndRetrievesSession() {
        Session session = manager.createSession();

        Optional<Session> retrieved = manager.getSession(session.getPublicId());
        assertTrue(retrieved.isPresent());
        assertEquals(session.getInternalId(), retrieved.get().getInternalId());
        assertEquals(SessionStatus.INIT, retrieved.get().getStatus());
    }

    @Test
    void rotatesPublicIdAndPreservesAttributes() {
        Session initial = manager.createSession();
        SessionIdentifier oldId = initial.getPublicId();
        TypedAttributeKey<String> languageKey =
                TypedAttributeKey.of("lang", String.class);
        initial.getAttributes().put(languageKey, "es-AR");

        Session rotated = manager.rotatePublicId(oldId);

        assertNotEquals(oldId, rotated.getPublicId());
        assertEquals(initial.getInternalId(), rotated.getInternalId());
        assertFalse(manager.getSession(oldId).isPresent());
        assertEquals("es-AR", rotated.getAttributes().get(languageKey).get());
    }

    @Test
    void authenticatesSessionAndIndexesItForUser() {
        Session anonymous = manager.createSession();

        UserSession authenticated =
                manager.authenticateSession(anonymous.getPublicId(), "test-user");

        assertEquals(SessionStatus.AUTHENTICATED, authenticated.getStatus());
        assertEquals("test-user", authenticated.getUserId());
        assertEquals(1, manager.getSessionsForUser("test-user").size());
    }
}

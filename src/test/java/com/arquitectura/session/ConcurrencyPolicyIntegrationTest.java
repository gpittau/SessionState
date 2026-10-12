package com.arquitectura.session;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConcurrencyPolicyIntegrationTest {
    private static HazelcastInstance hazelcast;

    @BeforeAll
    static void startHazelcast() {
        Config config = new Config();
        config.setClusterName("session-concurrency-tests");
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

    private UserSessionManager createManagerWithPolicy(
            UserSessionConcurrencyPolicy policy) {
        hazelcast.getMap("concurrencyMap").clear();
        hazelcast.getMultiMap("concurrencyIndex").clear();

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

        SessionBackendSPI backend =
                new LegacyHazelcastBackend(
                        hazelcast, "concurrencyMap", "concurrencyIndex");
        return new GenericSessionManager(backend, expiration, policy);
    }

    @Test
    void singleRejectKeepsFirstSessionAndRejectsSecondLogin() {
        UserSessionManager manager =
                createManagerWithPolicy(UserSessionConcurrencyPolicy.SINGLE_REJECT);
        String userId = "test-user";

        Session first = manager.createSession();
        manager.authenticateSession(first.getPublicId(), userId);

        Session second = manager.createSession();
        assertThrows(
                ConcurrentSessionRejectedException.class,
                () -> manager.authenticateSession(second.getPublicId(), userId));
        assertEquals(1, manager.getSessionsForUser(userId).size());
    }

    @Test
    void singleReplaceInvalidatesFirstSession() {
        UserSessionManager manager =
                createManagerWithPolicy(UserSessionConcurrencyPolicy.SINGLE_REPLACE);
        String userId = "test-user";

        Session first = manager.createSession();
        UserSession firstAuthenticated =
                manager.authenticateSession(first.getPublicId(), userId);

        Session second = manager.createSession();
        UserSession secondAuthenticated =
                manager.authenticateSession(second.getPublicId(), userId);

        List<UserSession> activeSessions = manager.getSessionsForUser(userId);
        assertEquals(1, activeSessions.size());
        assertEquals(
                secondAuthenticated.getInternalId(),
                activeSessions.get(0).getInternalId());
        assertFalse(manager.getSession(firstAuthenticated.getPublicId()).isPresent());
    }

    @Test
    void multiplePolicyAllowsConcurrentSessions() {
        UserSessionManager manager =
                createManagerWithPolicy(UserSessionConcurrencyPolicy.MULTIPLE);
        String userId = "test-user";

        manager.authenticateSession(manager.createSession().getPublicId(), userId);
        manager.authenticateSession(manager.createSession().getPublicId(), userId);
        manager.authenticateSession(manager.createSession().getPublicId(), userId);

        assertEquals(3, manager.getSessionsForUser(userId).size());
    }
}

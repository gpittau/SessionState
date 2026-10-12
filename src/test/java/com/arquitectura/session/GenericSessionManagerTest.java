package com.arquitectura.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenericSessionManagerTest {
    @Test
    void createsAuthenticatesRotatesStoresAttributesAndInvalidates() {
        InMemoryBackend backend = new InMemoryBackend();
        GenericSessionManager manager = new GenericSessionManager(
                backend,
                new ExpirationManager(new SessionExpirationPolicy() {
                    @Override
                    public Duration getIdleTimeout() {
                        return Duration.ofHours(1);
                    }

                    @Override
                    public Optional<Duration> getAbsoluteTimeout() {
                        return Optional.empty();
                    }
                }),
                UserSessionConcurrencyPolicy.MULTIPLE);

        Session created = manager.createSession();
        SessionIdentifier originalId = created.getPublicId();
        UserSession authenticated =
                manager.authenticateSession(originalId, "user-1");

        assertEquals(SessionStatus.AUTHENTICATED, authenticated.getStatus());
        assertEquals("user-1", authenticated.getUserId());
        assertNotEquals(originalId, authenticated.getPublicId());
        assertFalse(manager.getSession(originalId).isPresent());
        assertTrue(manager.getSession(authenticated.getPublicId()).isPresent());
        assertEquals(1, manager.getSessionsForUser("user-1").size());

        TypedAttributeKey<String> displayName =
                TypedAttributeKey.of("display-name", String.class);
        authenticated.getAttributes().put(displayName, "Ada");
        assertEquals(Optional.of("Ada"), authenticated.getAttributes().get(displayName));

        authenticated.invalidate();
        assertFalse(manager.getSession(authenticated.getPublicId()).isPresent());
        assertTrue(manager.getSessionsForUser("user-1").isEmpty());
    }

    private static final class InMemoryBackend implements SessionBackendSPI {
        private final Map<String, String> routes = new HashMap<>();
        private final Map<String, SessionStateDto> states = new HashMap<>();
        private final Map<String, Set<String>> userSessions = new HashMap<>();
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        @Override
        public Optional<String> getInternalId(String publicId) {
            return Optional.ofNullable(routes.get(publicId));
        }

        @Override
        public boolean mapRouteIfAbsent(String publicId, String internalId) {
            return routes.putIfAbsent(publicId, internalId) == null;
        }

        @Override
        public void deleteRoute(String publicId) {
            routes.remove(publicId);
        }

        @Override
        public void saveState(SessionStateDto state) {
            states.put(state.internalId, state);
        }

        @Override
        public Optional<SessionStateDto> getState(String internalId) {
            return Optional.ofNullable(states.get(internalId));
        }

        @Override
        public void deleteState(String internalId) {
            states.remove(internalId);
        }

        @Override
        public void indexUserSession(String userId, String internalId) {
            userSessions.computeIfAbsent(userId, ignored -> new HashSet<>()).add(internalId);
        }

        @Override
        public void removeUserSession(String userId, String internalId) {
            Set<String> ids = userSessions.get(userId);
            if (ids != null) {
                ids.remove(internalId);
            }
        }

        @Override
        public List<String> getInternalIdsForUser(String userId) {
            Collection<String> ids = userSessions.get(userId);
            return ids == null ? Collections.emptyList() : new ArrayList<>(ids);
        }

        @Override
        public DistributedLock acquireLock(String lockKey, Duration maxLeaseTime) {
            return () -> { };
        }

        @Override
        public <T> Optional<T> getAttribute(
                String internalId, String key, Class<T> type) {
            return Optional.ofNullable(attributes.get(attributeKey(internalId, key)))
                    .map(type::cast);
        }

        @Override
        public void putAttribute(String internalId, String key, Object value) {
            attributes.put(attributeKey(internalId, key), value);
        }

        @Override
        public void removeAttribute(String internalId, String key) {
            attributes.remove(attributeKey(internalId, key));
        }

        @Override
        public void clearAllAttributes(String internalId) {
            String prefix = "A:" + internalId + ":";
            attributes.keySet().removeIf(key -> key.startsWith(prefix));
        }

        private String attributeKey(String internalId, String key) {
            return "A:" + internalId + ":" + key;
        }
    }
}

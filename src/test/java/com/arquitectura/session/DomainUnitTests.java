package com.arquitectura.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DomainUnitTests {
    @Test
    public void testValueObjectEquality() {
        SessionIdentifier first = SessionIdentifier.of("token-123");
        SessionIdentifier second = SessionIdentifier.of("token-123");

        assertDoesNotThrow(() -> SessionIdentifier.of("token-456"));
        assertEquals(first, second, "Mismo valor debe producir igualdad de value objects");
        assertEquals(first.hashCode(), second.hashCode());
        assertThrows(NullPointerException.class, () -> SessionIdentifier.of(null));
    }

    @Test
    public void testExpirationManager() {
        SessionExpirationPolicy policy = new SessionExpirationPolicy() {
            @Override
            public Duration getIdleTimeout() {
                return Duration.ofMinutes(15);
            }

            @Override
            public Optional<Duration> getAbsoluteTimeout() {
                return Optional.of(Duration.ofHours(4));
            }
        };
        ExpirationManager manager = new ExpirationManager(policy);

        Instant creation = Instant.now().minus(Duration.ofHours(2));
        Instant recentAccess = Instant.now().minus(Duration.ofMinutes(5));
        Instant oldAccess = Instant.now().minus(Duration.ofMinutes(20));
        Instant veryOldCreation = Instant.now().minus(Duration.ofHours(5));

        assertFalse(manager.isExpired(creation, recentAccess));
        assertTrue(manager.isExpired(creation, oldAccess));
        assertTrue(manager.isExpired(veryOldCreation, Instant.now()));
    }

    @Test
    public void testSessionLifecycleFsmValidTransitions() {
        StateMachine<SessionStatus, SessionEvent, SessionContext> fsm =
                SessionLifecycleFsm.build();
        SessionContext context = new SessionContext("user-1");

        SessionStatus authenticated =
                fsm.fire(SessionStatus.INIT, SessionEvent.AUTHENTICATE, context);
        assertEquals(SessionStatus.AUTHENTICATED, authenticated);

        SessionStatus expired = fsm.fire(
                authenticated, SessionEvent.EXPIRE, context);
        assertEquals(SessionStatus.EXPIRED, expired);
    }

    @Test
    public void testSessionLifecycleFsmInvalidTransitions() {
        StateMachine<SessionStatus, SessionEvent, SessionContext> fsm =
                SessionLifecycleFsm.build();
        SessionContext context = new SessionContext("user-1");

        assertThrows(IllegalStateException.class,
                () -> fsm.fire(
                        SessionStatus.AUTHENTICATED,
                        SessionEvent.AUTHENTICATE,
                        context));
        assertThrows(IllegalStateException.class,
                () -> fsm.fire(
                        SessionStatus.EXPIRED,
                        SessionEvent.REVOKE,
                        context));
    }
}

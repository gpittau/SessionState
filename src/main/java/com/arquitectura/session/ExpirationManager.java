package com.arquitectura.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public class ExpirationManager {
    private final SessionExpirationPolicy policy;

    public ExpirationManager(SessionExpirationPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    public boolean isExpired(Instant creationTime, Instant lastAccessTime) {
        Instant now = Instant.now();
        if (Duration.between(lastAccessTime, now).compareTo(policy.getIdleTimeout()) > 0) {
            return true;
        }
        return policy.getAbsoluteTimeout()
                .map(maxLife -> Duration.between(creationTime, now).compareTo(maxLife) > 0)
                .orElse(false);
    }
}

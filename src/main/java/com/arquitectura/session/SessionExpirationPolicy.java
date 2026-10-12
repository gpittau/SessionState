package com.arquitectura.session;

import java.time.Duration;
import java.util.Optional;

public interface SessionExpirationPolicy {
    Duration getIdleTimeout();

    Optional<Duration> getAbsoluteTimeout();
}

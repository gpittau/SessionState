package com.arquitectura.session;

import java.time.Instant;

public interface Session {
    SessionIdentifier getPublicId();

    InternalSessionIdentifier getInternalId();

    SessionStatus getStatus();

    Instant getCreationTime();

    Instant getLastAccessTime();

    AttributeStorage getAttributes();

    void invalidate();
}

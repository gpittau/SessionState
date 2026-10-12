package com.arquitectura.session;

import java.io.Serializable;
import java.time.Instant;

public class SessionStateDto implements Serializable {
    private static final long serialVersionUID = 1L;

    public final String internalId;
    public String currentPublicId;
    public String userId;
    public SessionStatus status;
    public final Instant creationTime;
    public Instant lastAccessTime;

    public SessionStateDto(String internalId, String currentPublicId, String userId,
                           SessionStatus status) {
        this.internalId = internalId;
        this.currentPublicId = currentPublicId;
        this.userId = userId;
        this.status = status;
        this.creationTime = Instant.now();
        this.lastAccessTime = this.creationTime;
    }
}

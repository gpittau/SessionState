package com.arquitectura.session;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface SessionBackendSPI {
    Optional<String> getInternalId(String publicId);

    boolean mapRouteIfAbsent(String publicId, String internalId);

    void deleteRoute(String publicId);

    void saveState(SessionStateDto state);

    Optional<SessionStateDto> getState(String internalId);

    void deleteState(String internalId);

    void indexUserSession(String userId, String internalId);

    void removeUserSession(String userId, String internalId);

    List<String> getInternalIdsForUser(String userId);

    DistributedLock acquireLock(String lockKey, Duration maxLeaseTime);

    interface DistributedLock extends AutoCloseable {
        @Override
        void close();
    }

    <T> Optional<T> getAttribute(String internalId, String key, Class<T> type);

    void putAttribute(String internalId, String key, Object value);

    void removeAttribute(String internalId, String key);

    void clearAllAttributes(String internalId);
}

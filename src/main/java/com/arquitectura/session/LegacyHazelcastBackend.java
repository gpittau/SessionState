package com.arquitectura.session;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.multimap.MultiMap;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LegacyHazelcastBackend implements SessionBackendSPI {
    private final IMap<String, Object> sharedMap;
    private final MultiMap<String, String> userSessionsIndex;

    public LegacyHazelcastBackend(HazelcastInstance hazelcast,
                                  String mapName,
                                  String multiMapName) {
        this.sharedMap = hazelcast.getMap(mapName);
        this.userSessionsIndex = hazelcast.getMultiMap(multiMapName);
    }

    @Override
    public Optional<String> getInternalId(String publicId) {
        return Optional.ofNullable((String) sharedMap.get("R:" + publicId));
    }

    @Override
    public boolean mapRouteIfAbsent(String publicId, String internalId) {
        return sharedMap.putIfAbsent("R:" + publicId, internalId) == null;
    }

    @Override
    public void deleteRoute(String publicId) {
        sharedMap.remove("R:" + publicId);
    }

    @Override
    public void saveState(SessionStateDto state) {
        sharedMap.put("S:" + state.internalId, state);
    }

    @Override
    public Optional<SessionStateDto> getState(String internalId) {
        return Optional.ofNullable((SessionStateDto) sharedMap.get("S:" + internalId));
    }

    @Override
    public void deleteState(String internalId) {
        sharedMap.remove("S:" + internalId);
    }

    @Override
    public void indexUserSession(String userId, String internalId) {
        userSessionsIndex.put(userId, internalId);
    }

    @Override
    public void removeUserSession(String userId, String internalId) {
        userSessionsIndex.remove(userId, internalId);
    }

    @Override
    public List<String> getInternalIdsForUser(String userId) {
        Collection<String> internalIds = userSessionsIndex.get(userId);
        return internalIds != null
                ? new ArrayList<>(internalIds)
                : Collections.emptyList();
    }

    @Override
    public DistributedLock acquireLock(String lockKey, Duration leaseTime) {
        sharedMap.lock(lockKey, leaseTime.toMillis(), TimeUnit.MILLISECONDS);
        return () -> {
            try {
                sharedMap.unlock(lockKey);
            } catch (IllegalMonitorStateException ignored) {
                // The lease may already have expired.
            }
        };
    }

    @Override
    public <T> Optional<T> getAttribute(String internalId, String key, Class<T> type) {
        return Optional.ofNullable(sharedMap.get(attributeKey(internalId, key)))
                .map(type::cast);
    }

    @Override
    public void putAttribute(String internalId, String key, Object value) {
        sharedMap.put(attributeKey(internalId, key), value);
    }

    @Override
    public void removeAttribute(String internalId, String key) {
        sharedMap.remove(attributeKey(internalId, key));
    }

    @Override
    public void clearAllAttributes(String internalId) {
        String prefix = "A:" + internalId + ":";
        Set<String> keys = sharedMap.keySet().stream()
                .filter(key -> key.startsWith(prefix))
                .collect(Collectors.toSet());
        keys.forEach(sharedMap::remove);
    }

    private String attributeKey(String internalId, String key) {
        return "A:" + internalId + ":" + key;
    }
}

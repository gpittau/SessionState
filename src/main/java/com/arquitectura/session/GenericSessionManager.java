package com.arquitectura.session;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class GenericSessionManager implements UserSessionManager {
    private final SessionBackendSPI backend;
    private final ExpirationManager expirationManager;
    private final UserSessionConcurrencyPolicy concurrencyPolicy;
    private final StateMachine<SessionStatus, SessionEvent, SessionContext> fsm;

    public GenericSessionManager(SessionBackendSPI backend,
                                 ExpirationManager expirationManager,
                                 UserSessionConcurrencyPolicy concurrencyPolicy) {
        this.backend = backend;
        this.expirationManager = expirationManager;
        this.concurrencyPolicy = concurrencyPolicy;
        this.fsm = SessionLifecycleFsm.build();
    }

    @Override
    public Session createSession() {
        String internalId = UUID.randomUUID().toString();
        String publicId = UUID.randomUUID().toString();
        SessionStateDto state =
                new SessionStateDto(internalId, publicId, null, SessionStatus.INIT);

        backend.saveState(state);
        backend.mapRouteIfAbsent(publicId, internalId);
        return buildSession(state);
    }

    @Override
    public Optional<Session> getSession(SessionIdentifier publicId) {
        return backend.getInternalId(publicId.getValue())
                .flatMap(backend::getState)
                .filter(this::checkExpirationAndClean)
                .map(this::buildSession);
    }

    @Override
    public Session rotatePublicId(SessionIdentifier currentPublicId) {
        String currentPublicIdValue = currentPublicId.getValue();
        String internalId = backend.getInternalId(currentPublicIdValue)
                .orElseThrow(() -> new IllegalArgumentException("Sesión inválida"));
        String newPublicId = UUID.randomUUID().toString();

        if (!backend.mapRouteIfAbsent(newPublicId, internalId)) {
            throw new IllegalStateException("Colisión CAS durante rotación");
        }

        SessionStateDto state = backend.getState(internalId)
                .orElseThrow(() -> new IllegalStateException("No se encontró el estado de la sesión"));
        state.currentPublicId = newPublicId;
        backend.saveState(state);
        backend.deleteRoute(currentPublicIdValue);
        return buildSession(state);
    }

    @Override
    public UserSession authenticateSession(SessionIdentifier publicId, String userId) {
        try (SessionBackendSPI.DistributedLock ignored =
                     backend.acquireLock("L:" + userId, Duration.ofSeconds(10))) {
            enforceConcurrencyPolicy(userId);

            Session rotated = rotatePublicId(publicId);
            SessionStateDto state = backend.getState(rotated.getInternalId().getValue())
                    .orElseThrow(() -> new IllegalStateException("No se encontró el estado de la sesión"));

            state.status = fsm.fire(
                    state.status, SessionEvent.AUTHENTICATE, new SessionContext(userId));
            state.userId = userId;

            backend.saveState(state);
            backend.indexUserSession(userId, state.internalId);
            return (UserSession) buildSession(state);
        }
    }

    @Override
    public List<UserSession> getSessionsForUser(String userId) {
        List<SessionStateDto> states = backend.getInternalIdsForUser(userId).stream()
                .map(backend::getState)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        Map<Boolean, List<SessionStateDto>> partitioned = states.stream()
                .collect(Collectors.partitioningBy(
                        state -> expirationManager.isExpired(
                                state.creationTime, state.lastAccessTime)));

        partitioned.get(true).forEach(state -> invalidateWithFsm(state, SessionEvent.EXPIRE));
        return partitioned.get(false).stream()
                .map(state -> (UserSession) buildSession(state))
                .collect(Collectors.toList());
    }

    @Override
    public void invalidateAllUserSessions(String userId) {
        backend.getInternalIdsForUser(userId).stream()
                .map(backend::getState)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(state -> invalidateWithFsm(state, SessionEvent.REVOKE));
    }

    @Override
    public void touch(SessionIdentifier publicId) {
        backend.getInternalId(publicId.getValue()).ifPresent(internalId ->
                backend.getState(internalId).ifPresent(state -> {
                    if (state.status == SessionStatus.INIT
                            || state.status == SessionStatus.AUTHENTICATED) {
                        state.lastAccessTime = Instant.now();
                        backend.saveState(state);
                    }
                }));
    }

    @Override
    public void invalidateSession(SessionIdentifier publicId) {
        backend.getInternalId(publicId.getValue())
                .flatMap(backend::getState)
                .ifPresent(state -> invalidateWithFsm(state, SessionEvent.REVOKE));
    }

    private void enforceConcurrencyPolicy(String userId) {
        if (concurrencyPolicy == UserSessionConcurrencyPolicy.MULTIPLE) {
            return;
        }
        List<UserSession> activeSessions = getSessionsForUser(userId);
        if (activeSessions.isEmpty()) {
            return;
        }

        if (concurrencyPolicy == UserSessionConcurrencyPolicy.SINGLE_REJECT) {
            throw new ConcurrentSessionRejectedException(
                    "El usuario ya posee una sesión activa.");
        }
        if (concurrencyPolicy == UserSessionConcurrencyPolicy.SINGLE_REPLACE) {
            invalidateAllUserSessions(userId);
        }
    }

    private boolean checkExpirationAndClean(SessionStateDto state) {
        if (expirationManager.isExpired(state.creationTime, state.lastAccessTime)) {
            invalidateWithFsm(state, SessionEvent.EXPIRE);
            return false;
        }
        return true;
    }

    private void invalidateWithFsm(SessionStateDto state, SessionEvent reason) {
        try {
            state.status = fsm.fire(state.status, reason, new SessionContext(state.userId));
            backend.deleteState(state.internalId);
            backend.deleteRoute(state.currentPublicId);
            if (state.userId != null) {
                backend.removeUserSession(state.userId, state.internalId);
            }
            backend.clearAllAttributes(state.internalId);
        } catch (IllegalStateException ignored) {
            // Un estado terminal ya no admite otra transición.
        }
    }

    private Session buildSession(SessionStateDto state) {
        AttributeStorage storage = new GenericAttributeStorage(state.internalId, backend);
        return state.userId != null
                ? new ImplUserSession(state, storage, this)
                : new ImplSession(state, storage, this);
    }
}

class ImplSession implements Session {
    final SessionStateDto state;
    final AttributeStorage storage;
    final GenericSessionManager manager;

    ImplSession(SessionStateDto state, AttributeStorage storage, GenericSessionManager manager) {
        this.state = state;
        this.storage = storage;
        this.manager = manager;
    }

    @Override
    public SessionIdentifier getPublicId() {
        return SessionIdentifier.of(state.currentPublicId);
    }

    @Override
    public InternalSessionIdentifier getInternalId() {
        return InternalSessionIdentifier.of(state.internalId);
    }

    @Override
    public SessionStatus getStatus() {
        return state.status;
    }

    @Override
    public Instant getCreationTime() {
        return state.creationTime;
    }

    @Override
    public Instant getLastAccessTime() {
        return state.lastAccessTime;
    }

    @Override
    public AttributeStorage getAttributes() {
        return storage;
    }

    @Override
    public void invalidate() {
        manager.invalidateSession(getPublicId());
    }
}

class ImplUserSession extends ImplSession implements UserSession {
    ImplUserSession(SessionStateDto state,
                    AttributeStorage storage,
                    GenericSessionManager manager) {
        super(state, storage, manager);
    }

    @Override
    public String getUserId() {
        return state.userId;
    }
}

class GenericAttributeStorage implements AttributeStorage {
    private final String internalId;
    private final SessionBackendSPI backend;

    GenericAttributeStorage(String internalId, SessionBackendSPI backend) {
        this.internalId = internalId;
        this.backend = backend;
    }

    @Override
    public <T> Optional<T> get(TypedAttributeKey<T> key) {
        return backend.getAttribute(internalId, key.getName(), key.getType());
    }

    @Override
    public <T> void put(TypedAttributeKey<T> key, T value) {
        backend.putAttribute(internalId, key.getName(), value);
    }

    @Override
    public <T> void remove(TypedAttributeKey<T> key) {
        backend.removeAttribute(internalId, key.getName());
    }

    @Override
    public void clear() {
        backend.clearAllAttributes(internalId);
    }
}

package com.arquitectura.session;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.util.List;
import java.util.Optional;

public class ObservableSessionManager implements UserSessionManager {
    private final UserSessionManager delegate;
    private final Tracer tracer;
    private final LongCounter authCounter;

    public ObservableSessionManager(UserSessionManager delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("session-manager");
        this.authCounter = openTelemetry.getMeter("session-metrics")
                .counterBuilder("session.authenticated")
                .build();
    }

    @Override
    public UserSession authenticateSession(SessionIdentifier publicId, String userId) {
        Span span = tracer.spanBuilder("authenticateSession")
                .setAttribute("user.id", userId)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            UserSession session = delegate.authenticateSession(publicId, userId);
            authCounter.add(1);
            return session;
        } catch (ConcurrentSessionRejectedException exception) {
            span.addEvent("Concurrency policy rejected").setStatus(StatusCode.ERROR);
            throw exception;
        } catch (RuntimeException exception) {
            span.recordException(exception).setStatus(StatusCode.ERROR);
            throw exception;
        } finally {
            span.end();
        }
    }

    @Override
    public Session createSession() {
        return delegate.createSession();
    }

    @Override
    public Optional<Session> getSession(SessionIdentifier id) {
        return delegate.getSession(id);
    }

    @Override
    public Session rotatePublicId(SessionIdentifier id) {
        return delegate.rotatePublicId(id);
    }

    @Override
    public List<UserSession> getSessionsForUser(String userId) {
        return delegate.getSessionsForUser(userId);
    }

    @Override
    public void invalidateAllUserSessions(String userId) {
        delegate.invalidateAllUserSessions(userId);
    }

    @Override
    public void touch(SessionIdentifier id) {
        delegate.touch(id);
    }

    @Override
    public void invalidateSession(SessionIdentifier id) {
        delegate.invalidateSession(id);
    }
}

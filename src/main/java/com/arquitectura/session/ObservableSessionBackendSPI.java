package com.arquitectura.session;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class ObservableSessionBackendSPI implements SessionBackendSPI {
    private final SessionBackendSPI delegate;
    private final Tracer tracer;
    private final DoubleHistogram latency;

    public ObservableSessionBackendSPI(SessionBackendSPI delegate, OpenTelemetry openTelemetry) {
        this.delegate = delegate;
        this.tracer = openTelemetry.getTracer("session-backend-spi");
        this.latency = openTelemetry.getMeter("session-metrics")
                .histogramBuilder("backend.io.duration")
                .setUnit("ms")
                .build();
    }

    @Override
    public DistributedLock acquireLock(String lockKey, Duration maxLeaseTime) {
        Span span = tracer.spanBuilder("backend.acquireLock")
                .setAttribute("lock.key", lockKey)
                .startSpan();
        long startNanos = System.nanoTime();
        try (Scope scope = span.makeCurrent()) {
            DistributedLock lock = delegate.acquireLock(lockKey, maxLeaseTime);
            return () -> {
                Span unlockSpan = tracer.spanBuilder("backend.releaseLock").startSpan();
                try {
                    lock.close();
                } finally {
                    unlockSpan.end();
                }
            };
        } finally {
            span.end();
            latency.record(
                    (System.nanoTime() - startNanos) / 1_000_000.0,
                    Attributes.of(AttributeKey.stringKey("op"), "acquireLock"));
        }
    }

    @Override
    public Optional<String> getInternalId(String publicId) {
        return delegate.getInternalId(publicId);
    }

    @Override
    public boolean mapRouteIfAbsent(String publicId, String internalId) {
        return delegate.mapRouteIfAbsent(publicId, internalId);
    }

    @Override
    public void deleteRoute(String publicId) {
        delegate.deleteRoute(publicId);
    }

    @Override
    public void saveState(SessionStateDto state) {
        delegate.saveState(state);
    }

    @Override
    public Optional<SessionStateDto> getState(String internalId) {
        return delegate.getState(internalId);
    }

    @Override
    public void deleteState(String internalId) {
        delegate.deleteState(internalId);
    }

    @Override
    public void indexUserSession(String userId, String internalId) {
        delegate.indexUserSession(userId, internalId);
    }

    @Override
    public void removeUserSession(String userId, String internalId) {
        delegate.removeUserSession(userId, internalId);
    }

    @Override
    public List<String> getInternalIdsForUser(String userId) {
        return delegate.getInternalIdsForUser(userId);
    }

    @Override
    public <T> Optional<T> getAttribute(String internalId, String key, Class<T> type) {
        return delegate.getAttribute(internalId, key, type);
    }

    @Override
    public void putAttribute(String internalId, String key, Object value) {
        delegate.putAttribute(internalId, key, value);
    }

    @Override
    public void removeAttribute(String internalId, String key) {
        delegate.removeAttribute(internalId, key);
    }

    @Override
    public void clearAllAttributes(String internalId) {
        delegate.clearAllAttributes(internalId);
    }
}

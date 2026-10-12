package com.arquitectura.session;

import java.util.Objects;

public final class SessionIdentifier {
    private final String value;

    private SessionIdentifier(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static SessionIdentifier of(String value) {
        return new SessionIdentifier(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SessionIdentifier)) {
            return false;
        }
        SessionIdentifier that = (SessionIdentifier) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

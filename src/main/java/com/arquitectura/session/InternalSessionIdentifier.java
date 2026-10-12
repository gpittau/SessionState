package com.arquitectura.session;

import java.util.Objects;

public final class InternalSessionIdentifier {
    private final String value;

    private InternalSessionIdentifier(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    public static InternalSessionIdentifier of(String value) {
        return new InternalSessionIdentifier(value);
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InternalSessionIdentifier)) {
            return false;
        }
        InternalSessionIdentifier that = (InternalSessionIdentifier) other;
        return value.equals(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}

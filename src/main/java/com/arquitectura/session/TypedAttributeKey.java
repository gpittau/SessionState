package com.arquitectura.session;

import java.util.Objects;

public final class TypedAttributeKey<T> {
    private final String name;
    private final Class<T> type;

    private TypedAttributeKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> TypedAttributeKey<T> of(String name, Class<T> type) {
        return new TypedAttributeKey<>(name, type);
    }

    public String getName() {
        return name;
    }

    public Class<T> getType() {
        return type;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TypedAttributeKey<?>)) {
            return false;
        }
        TypedAttributeKey<?> that = (TypedAttributeKey<?>) other;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}

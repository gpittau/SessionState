package com.arquitectura.session;

import java.util.Optional;

public interface AttributeStorage {
    <T> Optional<T> get(TypedAttributeKey<T> key);

    <T> void put(TypedAttributeKey<T> key, T value);

    <T> void remove(TypedAttributeKey<T> key);

    void clear();
}

package com.arquitectura.session;

public class ConcurrentSessionRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ConcurrentSessionRejectedException(String message) {
        super(message);
    }
}

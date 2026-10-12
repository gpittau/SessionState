package com.arquitectura.session;

public class SessionContext implements WorkflowContext {
    private final String userId;

    public SessionContext(String userId) {
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}

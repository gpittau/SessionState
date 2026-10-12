package com.arquitectura.session;

public final class SessionLifecycleFsm {
    private SessionLifecycleFsm() {
    }

    public static StateMachine<SessionStatus, SessionEvent, SessionContext> build() {
        return StateMachine.<SessionStatus, SessionEvent, SessionContext>builder()
                .from(SessionStatus.INIT).on(SessionEvent.AUTHENTICATE)
                .to(SessionStatus.AUTHENTICATED).and()
                .from(SessionStatus.INIT).on(SessionEvent.EXPIRE)
                .to(SessionStatus.EXPIRED).and()
                .from(SessionStatus.INIT).on(SessionEvent.REVOKE)
                .to(SessionStatus.INVALIDATED).and()
                .from(SessionStatus.AUTHENTICATED).on(SessionEvent.EXPIRE)
                .to(SessionStatus.EXPIRED).and()
                .from(SessionStatus.AUTHENTICATED).on(SessionEvent.REVOKE)
                .to(SessionStatus.INVALIDATED).and()
                .build();
    }
}

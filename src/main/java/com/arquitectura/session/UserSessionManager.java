package com.arquitectura.session;

import java.util.List;
import java.util.Optional;

public interface UserSessionManager {
    Session createSession();

    Optional<Session> getSession(SessionIdentifier publicId);

    Session rotatePublicId(SessionIdentifier currentPublicId);

    UserSession authenticateSession(SessionIdentifier publicId, String userId);

    List<UserSession> getSessionsForUser(String userId);

    void invalidateAllUserSessions(String userId);

    void touch(SessionIdentifier publicId);

    void invalidateSession(SessionIdentifier publicId);
}

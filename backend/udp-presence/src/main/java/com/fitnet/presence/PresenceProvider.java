package com.fitnet.presence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Read-only Gateway boundary. Snapshots are immutable. */
public interface PresenceProvider {
    enum Status { ONLINE, OFFLINE }
    record UserPresence(String userId, Status status, Instant lastSeen) { }
    List<UserPresence> getUsers();
    Optional<UserPresence> getUser(String userId);
}

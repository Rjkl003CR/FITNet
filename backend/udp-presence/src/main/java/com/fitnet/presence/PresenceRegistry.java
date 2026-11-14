package com.fitnet.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class PresenceRegistry implements PresenceProvider {
    private record Entry(UserPresence value, long nanos) { }
    private final ConcurrentHashMap<String, Entry> users = new ConcurrentHashMap<>();
    private final long timeoutNanos;
    public PresenceRegistry(Duration timeout) {
        timeoutNanos = timeout.toNanos();
        if (timeoutNanos <= 0) throw new IllegalArgumentException("Timeout must be positive");
    }
    public void recordHeartbeat(Heartbeat heartbeat) {
        users.compute(heartbeat.userId(), (id, old) -> {
            Entry updated = new Entry(new UserPresence(id, Status.ONLINE, Instant.now()), System.nanoTime());
            // Emit inside the per-user atomic update so expiry and reconnect logs
            // cannot be reordered by competing heartbeat/sweep threads.
            if (old == null) System.out.println(id + " ONLINE");
            else if (old.value.status() == Status.OFFLINE)
                System.out.println(id + " ONLINE (reconnected)");
            return updated;
        });
    }
    public void expireUsers() {
        users.forEach((id, ignored) -> users.computeIfPresent(id, (key, entry) -> {
            if (entry.value.status() == Status.ONLINE && System.nanoTime() - entry.nanos >= timeoutNanos) {
                System.out.println(key + " OFFLINE");
                return new Entry(new UserPresence(key, Status.OFFLINE, entry.value.lastSeen()), entry.nanos);
            }
            return entry;
        }));
    }
    @Override public List<UserPresence> getUsers() {
        return users.values().stream().map(Entry::value)
                .sorted(Comparator.comparing(UserPresence::userId)).toList();
    }
    @Override public Optional<UserPresence> getUser(String userId) {
        return Optional.ofNullable(users.get(userId)).map(Entry::value);
    }
}

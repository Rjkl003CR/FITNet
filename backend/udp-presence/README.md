# Member 02 - UDP Presence

Java 21 / Maven module using only JDK networking and concurrency APIs at runtime,
plus the parent's JUnit 5 dependency for tests. No database is required.

## Architecture and protocol

`HeartbeatClient` sends a real `DatagramPacket` through a `DatagramSocket`
immediately on start, then every 3 seconds. `PresenceServer` binds UDP port 5002
on all local interfaces and processes datagrams on a dedicated receiver thread.
Each datagram contains exactly one UTF-8 message, without a trailing newline:

```text
FITNET/1|HEARTBEAT|student01|1794614400000
```

The fields are protocol version, message type, user ID, and nonnegative client
Unix epoch milliseconds. IDs must match `[A-Za-z0-9_.@-]{1,64}`. Messages over
512 bytes, invalid UTF-8, unknown types/versions, invalid timestamps and extra
fields are ignored. The receive buffer includes an extra byte to detect oversized
datagrams instead of accepting a truncated prefix. Packet lengths reset on each receive.

`PresenceRegistry` uses `ConcurrentHashMap.compute` for atomic per-user heartbeat
and expiry updates. Valid heartbeats set ONLINE and refresh server-side lastSeen
(UTC Instant). A monotonic clock measures elapsed time: client timestamps never
control expiry. A scheduled sweep marks a user OFFLINE after 10 seconds without
a heartbeat, normally within a further 250ms sweep interval. Reconnection updates
the same entry to ONLINE. Offline entries retain lastSeen and remain registered.
`PresenceProvider` exposes optional individual snapshots and immutable sorted lists;
list reads are weakly consistent across users during concurrent updates.

Server/client `close()` methods are idempotent, close sockets, stop scheduled tasks,
and wait for workers to exit. CLI shutdown hooks run these methods on Ctrl+C.
Unexpected receive I/O failure is logged and stops the receiver safely; restart
with a new server instance. Client send failures are logged at DEBUG and retried
on the next scheduled tick. UDP has no acknowledgement or delivery guarantee.

## Build and run

From the repository root, first enter the Maven reactor:

```powershell
cd backend
mvn -pl udp-presence -am clean test
mvn -pl udp-presence -am package
java -cp udp-presence/target/classes com.fitnet.presence.PresenceServer
```

In two other terminals, from `backend`, start distinct users:

```powershell
java -cp udp-presence/target/classes com.fitnet.presence.HeartbeatClient student01 localhost 5002
java -cp udp-presence/target/classes com.fitnet.presence.HeartbeatClient student02 localhost 5002
```

Use the server's host/IP instead of localhost for different machines and allow
inbound UDP 5002 through the host firewall. A browser cannot send raw UDP;
these Java clients demonstrate the heartbeat transport.

### Configuration

```powershell
java -Dpresence.port=5002 -Dpresence.timeout.ms=10000 -Dpresence.sweep.ms=250 -cp udp-presence/target/classes com.fitnet.presence.PresenceServer
```

Programmatic constructors accept a server port (0 selects an ephemeral test port),
positive timeout and sweep Duration; the client accepts a positive heartbeat
interval Duration (default 3 seconds). Sweep/client intervals must be at least 1ms.
Keep the heartbeat interval below the timeout. Production defaults require no flags.

## Demonstrate online/offline and reconnect

The server prints status transitions only, with no output for repeated heartbeats:

```text
student01 ONLINE
student02 ONLINE
student01 OFFLINE
student01 ONLINE (reconnected)
```

Run both clients above, stop student01 with Ctrl+C, and wait 10 seconds after its
last heartbeat (plus up to the 250ms sweep interval) for OFFLINE. Leave student02
running to demonstrate simultaneous clients. Restart student01 to see
ONLINE (reconnected). Each offline period prints just one OFFLINE line.

The standalone server has no REST endpoint. Inspect its read-only provider
in-process, or use this executable JShell example from `backend`:

```java
// Start: jshell --class-path udp-presence/target/classes
import com.fitnet.presence.*;
var server = new PresenceServer();
server.start();
var client = new HeartbeatClient("localhost", 5002, "student01");
client.start();
Thread.sleep(1000);
server.presence().getUsers(); // ONLINE with server lastSeen
client.close();
Thread.sleep(10500);
server.presence().getUser("student01"); // OFFLINE, lastSeen unchanged
var reconnect = new HeartbeatClient("localhost", 5002, "student01");
reconnect.start();
Thread.sleep(1000);
server.presence().getUsers(); // ONLINE with newer lastSeen
reconnect.close();
server.close();
```

Stop any standalone server before the JShell demo to free port 5002.
Tests use real loopback UDP, ephemeral ports and short configurable timeouts.
They cover reception, multiple users, timestamp refresh, periodic heartbeats,
expiry, reconnection, malformed/oversized packets, concurrent clients and registry
access, lifecycle validation and shutdown/port reuse.

## Gateway and frontend integration contract (proposed, not implemented)

At implementation time `gateway` and `shared` contain Maven skeletons without
Java interfaces or a REST framework. No unrelated modules have been changed.
`com.fitnet.presence.PresenceProvider` is the minimal integration interface:

```java
List<UserPresence> getUsers();
Optional<UserPresence> getUser(String userId);
```

The future Gateway should depend on `com.fitnet:udp-presence:1.0.0-SNAPSHOT`,
own one `PresenceServer` instance, start it at Gateway startup, inject
`server.presence()` into its handlers, and close the server at shutdown.
An independently launched server does not share its registry with another JVM;
separate deployment would require an explicitly designed service boundary.

Proposed REST routes, served by that future Gateway:

| Route | Success | Missing user |
| --- | --- | --- |
| `GET /api/presence/users` | 200, JSON array (empty `[]` before any heartbeat) | n/a |
| `GET /api/presence/users/{userId}` | 200, one JSON object | 404 |

```json
[{"userId":"student01","status":"ONLINE","lastSeen":"2026-11-14T10:00:00Z"}]
```

Serialize lastSeen as ISO-8601 UTC and status as ONLINE/OFFLINE; offline records
remain in the list. The frontend can poll the Gateway and display these fields.
Authentication, authorization, CORS, frontend polling and mapping authenticated
sessions to heartbeat clients remain Gateway/frontend integration work. UDP IDs
are currently self-reported and unauthenticated; this academic protocol does not
prevent spoofing or replay. Any valid packet refreshes presence at receipt time.
No HTTP or WebSocket transport substitutes for UDP heartbeats.

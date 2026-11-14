package com.fitnet.presence;

import org.junit.jupiter.api.Test;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static com.fitnet.presence.PresenceProvider.Status.*;

class PresenceTest {
    @Test void logsUdpTransitionsOnlyWhileAnotherClientStaysOnline() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        var original = System.out;
        try (var console = new java.io.PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(console);
            try (PresenceServer server = server(300);
                 HeartbeatClient second = new HeartbeatClient("127.0.0.1", server.getPort(), "student02", Duration.ofMillis(20))) {
                send(server, "student01");
                await(() -> server.presence().getUser("student01").isPresent());
                second.start();
                await(() -> server.presence().getUser("student02").isPresent());
                var firstSeen = server.presence().getUser("student01").orElseThrow().lastSeen();
                Thread.sleep(5);
                send(server, "student01");
                await(() -> server.presence().getUser("student01").orElseThrow().lastSeen().isAfter(firstSeen));
                await(() -> server.presence().getUser("student01").orElseThrow().status() == OFFLINE);
                Thread.sleep(350); // Multiple sweeps must not repeat OFFLINE.
                assertEquals(ONLINE, server.presence().getUser("student02").orElseThrow().status());
                send(server, "student01");
                await(() -> server.presence().getUser("student01").orElseThrow().status() == ONLINE);
                var reconnectedSeen = server.presence().getUser("student01").orElseThrow().lastSeen();
                Thread.sleep(5);
                send(server, "student01");
                await(() -> server.presence().getUser("student01").orElseThrow().lastSeen().isAfter(reconnectedSeen));
            }
        } finally { System.setOut(original); }
        assertEquals(java.util.List.of("student01 ONLINE", "student02 ONLINE", "student01 OFFLINE",
                "student01 ONLINE (reconnected)"), output.toString(java.nio.charset.StandardCharsets.UTF_8).lines().toList());
    }

    @Test void concurrentHeartbeatsLogFirstOnlineOnlyOnce() throws Exception {
        var output = new java.io.ByteArrayOutputStream();
        var original = System.out;
        try (var console = new java.io.PrintStream(output, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setOut(console);
            PresenceRegistry registry = new PresenceRegistry(Duration.ofSeconds(5));
            try (var pool = Executors.newFixedThreadPool(8)) {
                var tasks = new ArrayList<Callable<Void>>();
                for (int i = 0; i < 100; i++) tasks.add(() -> {
                    registry.recordHeartbeat(new Heartbeat("student01", 0));
                    registry.recordHeartbeat(new Heartbeat("student02", 0));
                    registry.expireUsers();
                    return null;
                });
                for (var future : pool.invokeAll(tasks)) future.get();
            }
            assertEquals(2, registry.getUsers().size());
        } finally { System.setOut(original); }
        assertEquals(java.util.List.of("student01 ONLINE", "student02 ONLINE"),
                output.toString(java.nio.charset.StandardCharsets.UTF_8).lines().sorted().toList());
    }

    private PresenceServer server(long timeout) throws Exception {
        PresenceServer server = new PresenceServer(0, Duration.ofMillis(timeout), Duration.ofMillis(10));
        server.start();
        return server;
    }
    private void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "Condition did not become true before deadline");
    }
    private void send(PresenceServer server, String id) throws Exception {
        try (HeartbeatClient client = new HeartbeatClient("127.0.0.1", server.getPort(), id)) {
            client.sendHeartbeat();
        }
    }
    private void raw(PresenceServer server, byte[] data) throws Exception {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), server.getPort()));
        }
    }
    @Test void receivesHeartbeatAndTracksServerLastSeen() throws Exception {
        try (PresenceServer server = server(5000)) {
            assertTrue(server.presence().getUser("alice").isEmpty());
            // Old client timestamp must not cause premature expiry.
            raw(server, new Heartbeat("alice", 0).encode());
            await(() -> server.presence().getUser("alice").isPresent());
            var first = server.presence().getUser("alice").orElseThrow();
            assertEquals(ONLINE, first.status());
            assertTrue(first.lastSeen().isAfter(java.time.Instant.now().minusSeconds(5)));
            Thread.sleep(5);
            send(server, "alice");
            await(() -> server.presence().getUser("alice").orElseThrow().lastSeen().isAfter(first.lastSeen()));
        }
    }
    @Test void multipleUsersAndImmutableSnapshots() throws Exception {
        try (PresenceServer server = server(5000)) {
            send(server, "alice"); send(server, "bob");
            await(() -> server.presence().getUsers().size() == 2);
            var snapshot = server.presence().getUsers();
            assertEquals(java.util.List.of("alice", "bob"), snapshot.stream().map(PresenceProvider.UserPresence::userId).toList());
            assertTrue(snapshot.stream().allMatch(user -> user.status() == ONLINE));
            assertThrows(UnsupportedOperationException.class, snapshot::clear);
        }
    }
    @Test void expiresAndReconnectsKeepingLastSeen() throws Exception {
        try (PresenceServer server = server(120)) {
            send(server, "alice");
            await(() -> server.presence().getUser("alice").isPresent());
            var seen = server.presence().getUser("alice").orElseThrow().lastSeen();
            await(() -> server.presence().getUser("alice").orElseThrow().status() == OFFLINE);
            assertEquals(seen, server.presence().getUser("alice").orElseThrow().lastSeen());
            send(server, "alice");
            await(() -> server.presence().getUser("alice").orElseThrow().status() == ONLINE);
            assertTrue(server.presence().getUser("alice").orElseThrow().lastSeen().isAfter(seen));
            assertEquals(1, server.presence().getUsers().size());
        }
    }
    @Test void scheduledClientKeepsUserOnlineThenExpiresAfterClose() throws Exception {
        try (PresenceServer server = server(300)) {
            try (HeartbeatClient client = new HeartbeatClient("127.0.0.1", server.getPort(), "alice", Duration.ofMillis(25))) {
                client.start(); client.start();
                await(() -> server.presence().getUser("alice").isPresent());
                var first = server.presence().getUser("alice").orElseThrow().lastSeen();
                Thread.sleep(500);
                assertEquals(ONLINE, server.presence().getUser("alice").orElseThrow().status());
                assertTrue(server.presence().getUser("alice").orElseThrow().lastSeen().isAfter(first));
            }
            await(() -> server.presence().getUser("alice").orElseThrow().status() == OFFLINE);
        }
    }
    @Test void invalidPacketsAreIgnoredAndReceiverSurvives() throws Exception {
        try (PresenceServer server = server(5000)) {
            for (String text : java.util.List.of("", "garbage", "FITNET/1|OTHER|evil|1", "FITNET/1|HEARTBEAT||1",
                    "FITNET/1|HEARTBEAT|evil|-1", "FITNET/1|HEARTBEAT|evil|9999999999999999999",
                    "FITNET/1|HEARTBEAT|evil|1|extra", "FITNET/1|HEARTBEAT|bad id|1")) {
                byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                assertThrows(IllegalArgumentException.class, () -> Heartbeat.decode(bytes, 0, bytes.length));
                raw(server, bytes);
            }
            raw(server, new byte[] {(byte) 0xc3, (byte) 0x28});
            byte[] oversized = new byte[1024];
            byte[] prefix = new Heartbeat("evil", 1).encode();
            System.arraycopy(prefix, 0, oversized, 0, prefix.length);
            raw(server, oversized);
            send(server, "valid");
            await(() -> server.presence().getUser("valid").isPresent());
            assertEquals(1, server.presence().getUsers().size());
        }
    }
    @Test void concurrentUdpClients() throws Exception {
        try (PresenceServer server = server(5000); var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 24; i++) {
                String id = "user" + i;
                tasks.add(() -> { send(server, id); return null; });
            }
            for (var future : pool.invokeAll(tasks)) future.get();
            await(() -> server.presence().getUsers().size() == 24);
            assertTrue(server.presence().getUsers().stream().allMatch(user -> user.status() == ONLINE));
        }
    }
    @Test void concurrentRegistryReadsHeartbeatsAndExpiry() throws Exception {
        PresenceRegistry registry = new PresenceRegistry(Duration.ofMillis(1));
        try (var pool = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<Callable<Void>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> {
                for (int j = 0; j < 1000; j++) {
                    registry.recordHeartbeat(new Heartbeat("shared", j));
                    registry.expireUsers();
                    assertEquals(1, registry.getUsers().size());
                    assertTrue(registry.getUser("shared").isPresent());
                }
                return null;
            });
            for (var future : pool.invokeAll(tasks)) future.get();
        }
        registry.recordHeartbeat(new Heartbeat("shared", 0));
        assertEquals(ONLINE, registry.getUser("shared").orElseThrow().status());
    }
    @Test void shutdownIsIdempotentReleasesPortAndStopsExpiry() throws Exception {
        PresenceServer server = server(150);
        int port = server.getPort();
        try {
            server.start();
            send(server, "alice");
            await(() -> server.presence().getUser("alice").isPresent());
            assertTimeoutPreemptively(Duration.ofSeconds(2), server::close);
            server.close();
            assertThrows(IllegalStateException.class, server::start);
            try (DatagramSocket rebound = new DatagramSocket(port)) { assertEquals(port, rebound.getLocalPort()); }
            var snapshot = server.presence().getUsers();
            Thread.sleep(200);
            assertEquals(snapshot, server.presence().getUsers());
        } finally { server.close(); }
    }
    @Test void validatesConfigurationAndClientLifecycle() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new PresenceServer(-1, Duration.ofSeconds(1), Duration.ofMillis(1)));
        assertThrows(IllegalArgumentException.class, () -> new PresenceRegistry(Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new PresenceServer(0, Duration.ofSeconds(1), Duration.ZERO));
        try (PresenceServer server = new PresenceServer()) {
            server.close();
            assertThrows(IllegalStateException.class, server::start);
        }
        HeartbeatClient client = new HeartbeatClient("localhost", 5002, "alice");
        client.close(); client.close();
        assertThrows(IllegalStateException.class, client::start);
        assertThrows(java.io.IOException.class, client::sendHeartbeat);
    }
}

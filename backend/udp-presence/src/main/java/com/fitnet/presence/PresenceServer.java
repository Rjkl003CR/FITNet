package com.fitnet.presence;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Single-use server; closing joins workers and releases the UDP port. */
public final class PresenceServer implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(PresenceServer.class.getName());
    private final int port;
    private final long sweepMillis;
    private final PresenceRegistry registry;
    private DatagramSocket socket;
    private ScheduledExecutorService scheduler;
    private Thread receiver;
    private boolean closed;
    public PresenceServer() { this(5002, Duration.ofSeconds(10), Duration.ofMillis(250)); }
    public PresenceServer(int port, Duration timeout, Duration sweep) {
        if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
        this.port = port;
        registry = new PresenceRegistry(timeout);
        sweepMillis = sweep.toMillis();
        if (sweepMillis < 1) throw new IllegalArgumentException("Sweep must be at least 1ms");
    }
    public synchronized void start() throws SocketException {
        if (closed) throw new IllegalStateException("Server is closed");
        if (socket != null) return;
        socket = new DatagramSocket(port);
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(registry::expireUsers, sweepMillis, sweepMillis, TimeUnit.MILLISECONDS);
        receiver = new Thread(() -> receive(socket), "udp-presence-receiver");
        receiver.start();
    }
    private void receive(DatagramSocket bound) {
        byte[] buffer = new byte[Heartbeat.MAX_BYTES + 1];
        while (!bound.isClosed()) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            try {
                bound.receive(packet);
                registry.recordHeartbeat(Heartbeat.decode(packet.getData(), packet.getOffset(), packet.getLength()));
            } catch (IllegalArgumentException e) {
                LOG.log(System.Logger.Level.DEBUG, "Ignored invalid heartbeat", e);
            } catch (IOException e) {
                if (!bound.isClosed()) LOG.log(System.Logger.Level.WARNING, "UDP receive failed", e);
                break;
            }
        }
        bound.close();
    }
    public PresenceProvider presence() { return registry; }
    public synchronized int getPort() {
        if (socket == null) throw new IllegalStateException("Server not started");
        return socket.getLocalPort();
    }
    @Override public synchronized void close() {
        closed = true;
        if (socket != null) socket.close();
        if (scheduler != null) scheduler.shutdownNow();
        boolean interrupted = false;
        if (receiver != null) while (receiver.isAlive()) {
            try { receiver.join(); } catch (InterruptedException e) { interrupted = true; }
        }
        if (scheduler != null) while (!scheduler.isTerminated()) {
            try { scheduler.awaitTermination(1, TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
    public static void main(String[] args) throws Exception {
        PresenceServer server = new PresenceServer(Integer.parseInt(System.getProperty("presence.port", "5002")),
                Duration.ofMillis(Long.parseLong(System.getProperty("presence.timeout.ms", "10000"))),
                Duration.ofMillis(Long.parseLong(System.getProperty("presence.sweep.ms", "250"))));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        System.out.println("UDP Presence listening on port " + server.getPort());
    }
}

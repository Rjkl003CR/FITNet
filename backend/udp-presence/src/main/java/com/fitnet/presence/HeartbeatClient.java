package com.fitnet.presence;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class HeartbeatClient implements AutoCloseable {
    private final String userId;
    private final InetSocketAddress destination;
    private final DatagramSocket socket;
    private final long intervalMillis;
    private ScheduledExecutorService scheduler;
    private boolean closed;
    public HeartbeatClient(String host, int port, String userId) throws SocketException {
        this(host, port, userId, Duration.ofSeconds(3));
    }
    public HeartbeatClient(String host, int port, String userId, Duration interval) throws SocketException {
        new Heartbeat(userId, 0);
        this.userId = userId;
        destination = new InetSocketAddress(host, port);
        if (destination.isUnresolved() || port == 0) throw new IllegalArgumentException("Invalid destination");
        intervalMillis = interval.toMillis();
        if (intervalMillis < 1) throw new IllegalArgumentException("Interval must be at least 1ms");
        socket = new DatagramSocket();
    }
    public synchronized void start() {
        if (closed) throw new IllegalStateException("Client is closed");
        if (scheduler != null) return;
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            try { sendHeartbeat(); }
            catch (IOException e) {
                System.getLogger(HeartbeatClient.class.getName()).log(System.Logger.Level.DEBUG, "Send failed", e);
            }
        }, 0, intervalMillis, TimeUnit.MILLISECONDS);
    }
    public void sendHeartbeat() throws IOException {
        byte[] bytes = new Heartbeat(userId, System.currentTimeMillis()).encode();
        socket.send(new DatagramPacket(bytes, bytes.length, destination));
    }
    @Override public synchronized void close() {
        closed = true;
        if (scheduler != null) scheduler.shutdownNow();
        socket.close();
        boolean interrupted = false;
        if (scheduler != null) while (!scheduler.isTerminated()) {
            try { scheduler.awaitTermination(1, TimeUnit.SECONDS); }
            catch (InterruptedException e) { interrupted = true; }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3)
            throw new IllegalArgumentException("Usage: HeartbeatClient <userId> [host] [port]");
        HeartbeatClient client = new HeartbeatClient(args.length > 1 ? args[1] : "localhost",
                args.length > 2 ? Integer.parseInt(args[2]) : 5002, args[0]);
        Runtime.getRuntime().addShutdownHook(new Thread(client::close));
        client.start();
        System.out.println("Sending heartbeats for " + args[0] + " every 3 seconds");
    }
}

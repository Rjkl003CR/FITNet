package com.fitnet.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpMessagingTest {
    @Test
    @Timeout(10)
    void broadcastsMessagesBetweenMultipleClients() throws Exception {
        TcpMessagingServer server = new TcpMessagingServer(0);
        Thread serverThread = Thread.ofVirtual().name("tcp-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> firstMessages = new LinkedBlockingQueue<>();
            BlockingQueue<String> secondMessages = new LinkedBlockingQueue<>();

            try (TcpMessagingClient first = new TcpMessagingClient("127.0.0.1", server.getPort(), firstMessages::add);
                 TcpMessagingClient second = new TcpMessagingClient("127.0.0.1", server.getPort(), secondMessages::add)) {
                awaitConnectedCount(server, 2);

                String message = "{\"messageId\":\"m1\",\"type\":\"PRIVATE_MESSAGE\",\"senderId\":\"u1\",\"senderName\":\"Alex\",\"receiverId\":\"u2\",\"groupId\":null,\"content\":\"Hello\",\"timestamp\":\"2026-11-21T18:55:31Z\"}";
                first.send(message);

                assertEquals(message, firstMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(message, secondMessages.poll(3, TimeUnit.SECONDS));
            }

            awaitConnectedCount(server, 0);
        } finally {
            server.close();
            serverThread.join(3000);
        }
    }

    @Test
    @Timeout(10)
    void rejectsInvalidJsonBeforeBroadcast() throws Exception {
        TcpMessagingServer server = new TcpMessagingServer(0);
        Thread serverThread = Thread.ofVirtual().name("tcp-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> messages = new LinkedBlockingQueue<>();
            try (TcpMessagingClient client = new TcpMessagingClient("127.0.0.1", server.getPort(), messages::add)) {
                awaitConnectedCount(server, 1);
                client.send("{\"not\":\"a fitnet message\"}");
                assertEquals(null, messages.poll(500, TimeUnit.MILLISECONDS));
            }
        } finally {
            server.close();
            serverThread.join(3000);
        }
    }

    private static void awaitPort(TcpMessagingServer server) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.getPort() == 0 && System.nanoTime() < deadline) Thread.sleep(10);
        assertTrue(server.getPort() > 0);
    }

    private static void awaitConnectedCount(TcpMessagingServer server, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.getConnectedClientCount() != expected && System.nanoTime() < deadline) Thread.sleep(10);
        assertEquals(expected, server.getConnectedClientCount());
    }
}

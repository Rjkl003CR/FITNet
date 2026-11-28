package com.fitnet.messaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TcpMessagingTest {
    @Test
    @Timeout(10)
    void broadcastsMessagesBetweenThreeClientsSimultaneously() throws Exception {
        TcpMessagingServer server = new TcpMessagingServer(0);
        Thread serverThread = Thread.ofVirtual().name("tcp-nio-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> firstMessages = new LinkedBlockingQueue<>();
            BlockingQueue<String> secondMessages = new LinkedBlockingQueue<>();
            BlockingQueue<String> thirdMessages = new LinkedBlockingQueue<>();

            try (TcpMessagingClient first = new TcpMessagingClient("127.0.0.1", server.getPort(), firstMessages::add);
                 TcpMessagingClient second = new TcpMessagingClient("127.0.0.1", server.getPort(), secondMessages::add);
                 TcpMessagingClient third = new TcpMessagingClient("127.0.0.1", server.getPort(), thirdMessages::add)) {
                awaitConnectedCount(server, 3);
                assertTrue(first.isConnected());
                assertTrue(second.isConnected());
                assertTrue(third.isConnected());

                String privateMessage = "{\"messageId\":\"m1\",\"type\":\"PRIVATE_MESSAGE\",\"senderId\":\"u1\",\"senderName\":\"Alex\",\"receiverId\":\"u2\",\"groupId\":null,\"content\":\"Hello\",\"timestamp\":\"2026-11-21T18:55:31Z\"}";
                String groupMessage = "{\"messageId\":\"m2\",\"type\":\"GROUP_MESSAGE\",\"senderId\":\"u2\",\"senderName\":\"Maya\",\"receiverId\":null,\"groupId\":\"net-lab\",\"content\":\"Ready for NIO demo\",\"timestamp\":\"2026-11-21T18:55:32Z\"}";

                first.send(privateMessage);
                assertEquals(privateMessage, firstMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(privateMessage, secondMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(privateMessage, thirdMessages.poll(3, TimeUnit.SECONDS));

                second.send(groupMessage);
                assertEquals(groupMessage, firstMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(groupMessage, secondMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(groupMessage, thirdMessages.poll(3, TimeUnit.SECONDS));
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
        Thread serverThread = Thread.ofVirtual().name("tcp-nio-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> messages = new LinkedBlockingQueue<>();
            try (TcpMessagingClient client = new TcpMessagingClient("127.0.0.1", server.getPort(), messages::add)) {
                awaitConnectedCount(server, 1);
                client.send("{\"not\":\"a fitnet message\"}");
                assertNull(messages.poll(400, TimeUnit.MILLISECONDS));
            }
        } finally {
            server.close();
            serverThread.join(3000);
        }
    }

    @Test
    @Timeout(10)
    void reassemblesFragmentedUtf8AndCoalescedFramesOverSocketChannel() throws Exception {
        TcpMessagingServer server = new TcpMessagingServer(0);
        Thread serverThread = Thread.ofVirtual().name("tcp-nio-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> receiverMessages = new LinkedBlockingQueue<>();

            try (TcpMessagingClient receiver = new TcpMessagingClient("127.0.0.1", server.getPort(), receiverMessages::add);
                 SocketChannel rawSender = SocketChannel.open(new InetSocketAddress("127.0.0.1", server.getPort()))) {
                awaitConnectedCount(server, 2);

                String firstMsg = "{\"messageId\":\"frag-1\",\"type\":\"PRIVATE_MESSAGE\",\"senderId\":\"u1\",\"senderName\":\"Alex\",\"receiverId\":\"u2\",\"groupId\":null,\"content\":\"Split UTF-8: \u2713\",\"timestamp\":\"2026-11-28T10:00:00Z\"}";
                String secondMsg = "{\"messageId\":\"frag-2\",\"type\":\"SYSTEM\",\"senderId\":\"sys\",\"senderName\":\"System\",\"receiverId\":null,\"groupId\":null,\"content\":\"Coalesced line\",\"timestamp\":\"2026-11-28T10:00:01Z\"}";

                byte[] combined = (firstMsg + "\n" + secondMsg + "\n").getBytes(StandardCharsets.UTF_8);
                // Split mid-message and mid-UTF-8 sequence (\u2713 is 3 bytes in UTF-8)
                int splitIndex = firstMsg.getBytes(StandardCharsets.UTF_8).length - 20;

                writeFully(rawSender, ByteBuffer.wrap(combined, 0, splitIndex));
                assertNull(receiverMessages.poll(150, TimeUnit.MILLISECONDS));

                writeFully(rawSender, ByteBuffer.wrap(combined, splitIndex, combined.length - splitIndex));
                assertEquals(firstMsg, receiverMessages.poll(3, TimeUnit.SECONDS));
                assertEquals(secondMsg, receiverMessages.poll(3, TimeUnit.SECONDS));
            }

            awaitConnectedCount(server, 0);
        } finally {
            server.close();
            serverThread.join(3000);
        }
    }

    @Test
    @Timeout(10)
    void continuesServingRemainingClientsAfterAbruptDisconnect() throws Exception {
        TcpMessagingServer server = new TcpMessagingServer(0);
        Thread serverThread = Thread.ofVirtual().name("tcp-nio-test-server").start(server::startUnchecked);

        try {
            awaitPort(server);
            BlockingQueue<String> survivorMessages = new LinkedBlockingQueue<>();

            TcpMessagingClient transientClient = new TcpMessagingClient("127.0.0.1", server.getPort(), m -> {});
            try (TcpMessagingClient survivor = new TcpMessagingClient("127.0.0.1", server.getPort(), survivorMessages::add)) {
                awaitConnectedCount(server, 2);
                transientClient.close();
                assertFalse(transientClient.isConnected());
                awaitConnectedCount(server, 1);

                String message = "{\"messageId\":\"after-close\",\"type\":\"PRIVATE_MESSAGE\",\"senderId\":\"u1\",\"senderName\":\"Alex\",\"receiverId\":\"u2\",\"groupId\":null,\"content\":\"Still connected\",\"timestamp\":\"2026-11-28T10:05:00Z\"}";
                survivor.send(message);
                assertEquals(message, survivorMessages.poll(3, TimeUnit.SECONDS));
            }
            awaitConnectedCount(server, 0);
        } finally {
            server.close();
            serverThread.join(3000);
        }
    }

    private static void writeFully(SocketChannel channel, ByteBuffer buffer) throws Exception {
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
    }

    private static void awaitPort(TcpMessagingServer server) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.getPort() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertTrue(server.getPort() > 0);
    }

    private static void awaitConnectedCount(TcpMessagingServer server, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.getConnectedClientCount() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, server.getConnectedClientCount());
    }
}

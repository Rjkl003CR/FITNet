# TCP Messaging (Java NIO)

The TCP messaging broker accepts UTF-8, line-delimited JSON messages on port
`6000` (configurable via `-Dtcp.port`) and broadcasts validated messages to
all connected TCP clients. The Java Gateway connects to this broker via
`TcpMessagingClient`; browser clients connect to the Gateway WebSocket instead
of opening raw TCP sockets.

## Java NIO Implementation

- **`TcpMessagingServer`**: Non-blocking event-loop broker using
  `ServerSocketChannel`, `Selector` (`OP_ACCEPT`, `OP_READ`, `OP_WRITE`),
  `SelectionKey`, non-blocking `SocketChannel`s, and `ByteBuffer` framing with
  per-client read/write buffers so partial reads and partial writes never block
  the selector thread.
- **`TcpMessagingClient`**: Non-blocking NIO client using `SocketChannel`
  (`OP_CONNECT`, `OP_READ`, `OP_WRITE`), `Selector`, and `ByteBuffer`s to send
  and receive newline-delimited UTF-8 JSON frames.

## Build and Test

From `C:\FITNet\FITNet\backend`:

```powershell
mvn -pl tcp-messaging -am clean test
mvn clean test
```

## Run and Demonstrate

1. Start the NIO TCP Messaging server:

```powershell
cd C:\FITNet\FITNet\backend
mvn -pl tcp-messaging -am compile
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingServer
```

Use `"-Dtcp.port=6000"` in PowerShell (or `-Dtcp.port=6000` in bash) to choose a custom port.

2. Connect multiple NIO clients in separate terminals (e.g., 3 clients):

```powershell
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingClient student-1
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingClient student-2
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingClient student-3
```

Type any text (or raw FITNet JSON) in any client terminal and press Enter; the
NIO `Selector` server broadcasts the validated JSON message to all connected
clients in real time.

3. Optional full-stack integration with Gateway and Frontend:

```powershell
# Terminal 1: TCP NIO broker (default port 6000)
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingServer

# Terminal 2: Java Gateway (HTTP 8080, WebSocket bridge 8081 -> TCP 6000)
mvn -pl gateway -am compile
java -cp "gateway\target\classes;tcp-messaging\target\classes;secure-transfer\target\classes" com.fitnet.gateway.GatewayServer
```

For direct Java integration:

```java
try (TcpMessagingClient client =
             new TcpMessagingClient("127.0.0.1", 6000, System.out::println)) {
    client.send("{\"messageId\":\"m1\",\"type\":\"PRIVATE_MESSAGE\","
            + "\"senderId\":\"u1\",\"senderName\":\"Alex\","
            + "\"receiverId\":\"u2\",\"groupId\":null,"
            + "\"content\":\"Hello\",\"timestamp\":\"2026-11-28T10:00:00Z\"}");
}
```

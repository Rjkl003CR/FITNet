# TCP Messaging

The TCP messaging broker accepts UTF-8, line-delimited JSON messages on port
`6000` and broadcasts validated messages to connected clients. The Gateway
uses this broker; browser clients should connect to the Gateway WebSocket
instead of opening a raw TCP socket.

## Build and run

From `backend`:

```powershell
mvn -pl tcp-messaging -am clean package
java -cp tcp-messaging\target\classes com.fitnet.messaging.TcpMessagingServer
```

Use `-Dtcp.port=6000` to select another port.

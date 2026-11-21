# FITNet Gateway

The Gateway exposes the browser WebSocket endpoint and bridges each connected
WebSocket to the TCP messaging broker.

```text
Browser WebSocket /ws/chat :8080
              |
              v
       FITNet Gateway
              |
              v
       TCP broker :6000
```

## Build and run

Start the TCP broker first. Then, from `backend`, run:

```powershell
mvn -pl gateway -am clean package
java -cp gateway\target\classes com.fitnet.gateway.GatewayServer
```

Defaults:

- Gateway WebSocket: `ws://localhost:8080/ws/chat`
- TCP broker: `localhost:6000`

Configuration:

```powershell
java -Dgateway.port=8080 -Dtcp.host=localhost -Dtcp.port=6000 `
  -cp gateway\target\classes com.fitnet.gateway.GatewayServer
```

The Gateway currently implements the TCP Messaging WebSocket bridge only.
Presence HTTP routes still require the separate Gateway integration described
in `backend/udp-presence/README.md`.

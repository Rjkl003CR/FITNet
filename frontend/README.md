# FITNet Frontend

Next.js frontend for the FITNet collaboration platform.

## Run locally

From `C:\FITNet\FITNet\frontend`:

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

Available routes:

- `/` - TCP Messaging
- `/presence` - UDP Presence

## Environment

Copy `.env.example` to `.env.local` and update the values for your Gateway:

```env
NEXT_PUBLIC_GATEWAY_WS_URL=ws://localhost:8080/ws/chat
NEXT_PUBLIC_PRESENCE_API_BASE_URL=http://localhost:8080
```

`NEXT_PUBLIC_GATEWAY_WS_URL` is used by the browser WebSocket client. The
frontend never opens a Java TCP socket directly; the Java Gateway owns the
WebSocket-to-TCP bridge.

`NEXT_PUBLIC_PRESENCE_API_BASE_URL` is used for:

```text
GET /api/presence/users
```

The Presence response must be a JSON array containing `userId`, `status`
(`ONLINE` or `OFFLINE`), and an ISO-8601 `lastSeen` value.

## TCP Messaging

The messaging screen connects to the Gateway when
`NEXT_PUBLIC_GATEWAY_WS_URL` is configured. It:

- Displays connection state and reconnects after unexpected disconnects.
- Validates incoming message payloads before rendering them.
- Builds conversations from messages received from the Gateway.
- Sends private and group messages using the shared message shape.
- Keeps the composer disabled while the Gateway is offline.

Expected message shape:

```json
{
  "messageId": "UUID",
  "type": "PRIVATE_MESSAGE",
  "senderId": "user-id",
  "senderName": "User Name",
  "receiverId": "receiver-id",
  "groupId": null,
  "content": "Hello!",
  "timestamp": "2026-11-14T10:00:00Z"
}
```

## UDP Presence

The Presence page polls the Gateway every five seconds. The browser does not
send raw UDP packets. The Java Presence server and Gateway must be running
before the page can show users.

## Checks and production run

```powershell
npm run lint
npx tsc --noEmit
npm run build
npm run start
```

`npm run start` serves the production build at
[http://localhost:3000](http://localhost:3000).

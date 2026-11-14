# UDP Presence frontend

Run `npm ci`, then `npm.cmd run dev` from `frontend` and visit
`http://localhost:3000/presence`. The existing home page is unchanged.

The page uses the existing neutral Tailwind light/dark palette with emerald
presence accents. It displays user IDs (the backend has no display-name field),
ONLINE/OFFLINE text indicators, local last-seen timestamps with UTC tooltips,
registered count and online count. Online users sort first. Layout adapts from
one to two columns. Loading, successful empty response, and unavailable-service
states are distinct. No mock records are used in the application.

## Gateway dependency

The Java Gateway is not implemented. Until it serves the endpoint, the page
shows an unavailable message, unknown counts (`—`), and no invented users.
The typed client requests `GET /api/presence/users` with JSON Accept and no cache.
Expected 200 response (empty array is valid):

```json
[{"userId":"student01","status":"ONLINE","lastSeen":"2026-11-14T10:00:00Z"}]
```

IDs must match the UDP backend's 1–64 character identifier format; statuses are
ONLINE/OFFLINE and lastSeen is an ISO timestamp including a timezone. Invalid
responses, duplicate IDs, HTML responses, network errors and HTTP failures are
treated as unavailable. Last-seen and status are server-authoritative; the UI
does not infer offline state from a browser clock.

Default requests are same-origin. Either arrange a reverse proxy for `/api/presence/users`
or set `NEXT_PUBLIC_PRESENCE_API_BASE_URL=http://localhost:8080` in `.env.local`
to the future Gateway origin, then restart/rebuild Next.js. A different origin
requires Gateway CORS permission for the frontend origin. This public variable
must contain no secrets. Authentication/cookie policy must be agreed with the
Gateway owner; this implementation does not invent an authentication scheme.

The Gateway must own/integrate the UDP server's `PresenceProvider` and serialize
its current registry. Starting a standalone UDP server alone does not expose HTTP.
Browsers cannot send DatagramSocket heartbeats: keep the Java heartbeat clients
for the live demo. No Next.js API stub or backend modification is included.

Polling starts on mount, repeats 5 seconds after each completed request and
uses an 8-second request timeout. Manual refresh shares the same request guard.
Unmount cancels requests and timers, including during React Strict Mode replay.
After a failed refresh, the last successful list/counts remain visibly marked
as stale and the page automatically retries. A successful response clears the warning.
Offline detection still uses the backend's 10-second timeout plus sweep and polling delays.

## Checks

From `frontend`:

```powershell
npm.cmd run lint
npx.cmd next typegen
npx.cmd tsc --noEmit
npx.cmd tsc --outDir .next/presence-tests --noEmit false --incremental false --module commonjs --moduleResolution node --target ES2020 --jsx react-jsx --esModuleInterop --skipLibCheck src/features/presence/api.ts src/features/presence/PresenceList.tsx src/features/presence/PresenceUserCard.tsx src/features/presence/PresenceStatus.tsx
node --test src/features/presence/presence.test.mjs
```

Tests use Node's built-in runner and installed React server rendering; no extra
dependencies or package scripts are added. Generated test JS stays in ignored
`.next/`. Fixtures are test-only. Tests cover response validation, endpoint/base
URL, cancellation propagation, unavailable responses, status text, empty state,
last-seen markup and sorting. For a live integration check, start the Gateway and
two Java heartbeat clients, then stop/restart one and observe the next refresh.

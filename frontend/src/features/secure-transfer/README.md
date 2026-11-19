# Secure file transfer UI

Run the Next.js frontend from `frontend` and visit `http://localhost:3000/files`.
The Java TLS server and Gateway must be running first; their startup and
certificate instructions are in `backend/secure-transfer/README.md`.

The browser requests the Gateway at `http://localhost:8080` by default. Set
`NEXT_PUBLIC_GATEWAY_BASE_URL` in `frontend/.env.local` to a different Gateway
origin and set the Gateway's `gateway.frontend.origin` property to the exact
frontend origin so CORS preflight requests are allowed. Restart Next.js after
changing the public environment variable.

The upload sends the selected file bytes as `application/octet-stream` and its
name in `X-Filename`. The page lists server metadata, uploads files up to 100 MB,
and downloads a file only after the Gateway's TLS client verifies its SHA-256.
The browser-to-Gateway link in this local demo is HTTP; the Java Gateway-to-file
server link uses TLS. Keep the Gateway on loopback or put HTTPS in front of it
before making the page available outside the local machine.

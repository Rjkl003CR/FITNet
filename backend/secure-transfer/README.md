# FITNet Secure File Transfer

The module transfers files over TLS using `SSLServerSocket` and `SSLSocket` on
port 5004. The Gateway connects to this service over TLS; the Next.js page calls
the Gateway over HTTP on port 8080.

## Create local development certificates

From the `backend` directory in PowerShell, set strong local passwords using
secure prompts, then run the certificate script. Passwords stay in the current
PowerShell process environment and are not written into the script or a file:

```powershell
$secureKeyPassword = Read-Host "Server keystore password (12+ characters)" -AsSecureString
$env:FITNET_TLS_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new("", $secureKeyPassword).Password
$secureTrustPassword = Read-Host "Client truststore password (12+ characters)" -AsSecureString
$env:FITNET_TLS_TRUSTSTORE_PASSWORD = [System.Net.NetworkCredential]::new("", $secureTrustPassword).Password
./secure-transfer/scripts/generate-dev-certs.ps1
```

This creates a PKCS12 server keystore (`server.p12`) containing the private key
and certificate, exports the public certificate (`server.cer`), then imports
that certificate into a PKCS12 client truststore (`truststore.p12`) inside the
default `secure-transfer\certs` directory. The self-signed development
certificate includes SAN entries for `localhost` and `127.0.0.1`. The client
uses the truststore and verifies the server hostname; there is no trust-all
mode. The script refuses to overwrite existing files. Generated certificate
files and common private-key formats are ignored by Git.

If stores already exist under `secure-transfer\certs` and you want a separate
fresh set without removing them, set a new output directory before running the
script and point `FITNET_TLS_KEYSTORE` and `FITNET_TLS_TRUSTSTORE` to that
directory in every terminal that starts the server or client:

```powershell
$env:FITNET_TLS_CERT_DIR = ".\secure-transfer\certs\phase01"
$env:FITNET_TLS_KEYSTORE = Join-Path $env:FITNET_TLS_CERT_DIR "server.p12"
$env:FITNET_TLS_TRUSTSTORE = Join-Path $env:FITNET_TLS_CERT_DIR "truststore.p12"
./secure-transfer/scripts/generate-dev-certs.ps1
```

Repeat the password prompt (and any custom path assignments) in every PowerShell
terminal that starts the TLS server or Java TLS client; process environment
variables are not automatically shared with other terminals.

The server and client read passwords from `FITNET_TLS_KEYSTORE_PASSWORD` and
`FITNET_TLS_TRUSTSTORE_PASSWORD`. Store paths can be supplied through
`FITNET_TLS_KEYSTORE` and `FITNET_TLS_TRUSTSTORE`, or overridden with the Java
system properties `secure.transfer.keystore` and `secure.transfer.truststore`.
The defaults are `secure-transfer/certs/server.p12` and
`secure-transfer/certs/truststore.p12` when run from `backend`.

Verify the store contents from `backend` without putting passwords in command
arguments (replace the `-keystore` path if you used a custom directory):

```powershell
keytool -list -v -storetype PKCS12 -keystore .\secure-transfer\certs\server.p12 `
  -storepass:env FITNET_TLS_KEYSTORE_PASSWORD
keytool -list -v -storetype PKCS12 -keystore .\secure-transfer\certs\truststore.p12 `
  -storepass:env FITNET_TLS_TRUSTSTORE_PASSWORD
```

For deployment, use CA-issued certificates and provide store paths/passwords via
the deployment secret manager.

## TLS handshake smoke test

The module's integration test starts the TLS server on an ephemeral local port,
completes a TLS 1.2 or TLS 1.3 handshake using the configured test truststore,
then sends a small `LIST` request and checks the encrypted response. It does not
upload or download a file. From `backend`, run:

```powershell
mvn -pl secure-transfer "-Dtest=SecureTransferTest#tlsHandshakeCompletesAndServerRespondsOverEncryptedSocket" test
```

## Build and run

From `backend`:

```powershell
mvn -pl secure-transfer -am package
java -cp secure-transfer/target/classes com.fitnet.transfer.SecureTransferServer
```

In another terminal, start the Gateway after the TLS server:

```powershell
mvn -pl gateway -am package
java -cp "gateway/target/classes;secure-transfer/target/classes" com.fitnet.gateway.GatewayServer
```

The stand-alone Java client can demonstrate transfers without the browser:

```powershell
java -cp secure-transfer/target/classes com.fitnet.transfer.SecureTransferClient list
java -cp secure-transfer/target/classes com.fitnet.transfer.SecureTransferClient upload .\sample.pdf
java -cp secure-transfer/target/classes com.fitnet.transfer.SecureTransferClient download sample.pdf .\downloaded-sample.pdf
```

The client prints the server-verified SHA-256 after upload and verifies the
download before writing the destination file. The server stores files in
`secure-transfer/storage` by default. Override `secure.transfer.port`, `secure.transfer.host`,
`secure.transfer.storage`, `secure.transfer.keystore`, or
`secure.transfer.truststore` as needed. Uploads are limited to 100 MiB by default.

Run the TLS module and Gateway tests from `backend` with
`mvn -pl gateway -am test`. Tests start a local TLS server using temporary
keystores and exercise file integrity, empty/text/binary files, concurrent
transfers, rejected certificates, and the HTTP-to-TLS Gateway flow.

## Gateway API

The local Gateway origin is `http://localhost:8080` and permits the configured
frontend origin (`http://localhost:3000` by default):

| Request | Result |
| --- | --- |
| `GET /api/files` | JSON list of file names, sizes, and SHA-256 hashes |
| `POST /api/files` with raw file bytes and `X-Filename` | Upload and integrity verification |
| `GET /api/files/{fileName}` | Download a verified file as an attachment |

The browser sends binary file bytes directly to the Gateway. It cannot create a
Java TLS socket; the Gateway uses the module's `SSLSocket` client to communicate
with the file server. The browser-to-Gateway leg uses HTTP in this local demo;
the Gateway binds to loopback (`127.0.0.1`) by default. Use an HTTPS reverse
proxy before exposing the browser-facing Gateway to a network. This sample has
no user authentication or authorization.

## Transfer protocol

Each TLS connection handles one request. The client defaults to protocol version
1 for compatibility with already deployed servers. Set
`"-Dsecure.transfer.protocol=2"` (quoted in PowerShell because the property name
contains dots) to opt into version 2. The server accepts both
versions, so existing version 1 clients continue to work after a server update.
Version 2 uses this binary header:

```text
magic (int, FNT1) | version (byte: 2) | operation (byte: 1 upload, 2 download, 3 list)
```

In version 2, each text field is an unsigned 16-bit byte length followed by
strict UTF-8. Filenames are limited to 128 safe ASCII bytes, status messages to
1024 bytes, and SHA-256 values to 64 lowercase hexadecimal bytes. An `UPLOAD`
request contains filename, signed 64-bit byte length, SHA-256, then exactly that
many raw file bytes. A `DOWNLOAD` request contains a filename; the response
contains status, filename, byte length, SHA-256, and exactly that many raw file
bytes. `LIST` returns the same filename, size, and SHA-256 metadata records.
File content is streamed as bytes and is never decoded as text.

Version 1 retains its original `writeUTF` operation and metadata fields. The
server keeps its version 1 response framing for those requests. Both versions
preserve the `FileMetadata(fileName, size, sha256)` Java contract and perform
the existing SHA-256 verification. Incomplete uploads are discarded; completed
files are atomically moved into storage. Downloads are written to a temporary
file and moved into place only after verification.

## Presentation demonstration

This walkthrough uses the existing certificate setup, Gateway API, and frontend
page described above. It runs on a local Windows development machine.

### Prerequisites and preparation

- JDK 21 or later and Maven 3.9 or later.
- Node.js and npm for the Next.js frontend.
- Development TLS stores generated with [the certificate instructions above](#create-local-development-certificates).
- Use `localhost` for the Gateway's TLS client. The development certificate has
  `localhost` and `127.0.0.1` subject alternative names.

If the stores have not been generated, open PowerShell in `backend` and follow
the certificate-generation commands above. The certificate script refuses to
overwrite existing stores. Keep the keystore and truststore passwords in your
PowerShell session; repeat the role-specific environment setup in each new
terminal because environment variables are not shared between terminals.

Build the Gateway and its Secure Transfer dependency once, from `backend`:

```powershell
mvn -pl gateway -am package
```

### Start the services

Open **Terminal A** in `backend`. Configure the server's keystore password,
then start the TLS server. By default, it loads `.\secure-transfer\certs\server.p12`
(set `$env:FITNET_TLS_KEYSTORE` only if you generated stores in a different
directory). This presentation command sets a 1 MiB server limit so a small
fixture can demonstrate downstream size rejection; quote JVM `-D` flags in
PowerShell so dots are not split into separate arguments. Omit
`"-Dsecure.transfer.max.bytes=1048576"` to use the normal 100 MiB server limit.

```powershell
$secureKeyPassword = Read-Host "Server keystore password" -AsSecureString
$env:FITNET_TLS_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new("", $secureKeyPassword).Password
$env:FITNET_TLS_KEYSTORE = ".\secure-transfer\certs\server.p12"
java "-Dsecure.transfer.max.bytes=1048576" -cp "secure-transfer/target/classes" com.fitnet.transfer.SecureTransferServer
```

Open **Terminal B** in `backend`. The Gateway needs the client truststore
(`.\secure-transfer\certs\truststore.p12` by default). This command opts the
Gateway's TLS client into protocol V2; omit `"-Dsecure.transfer.protocol=2"` to
run the default V1 client.

```powershell
$secureTrustPassword = Read-Host "Client truststore password" -AsSecureString
$env:FITNET_TLS_TRUSTSTORE_PASSWORD = [System.Net.NetworkCredential]::new("", $secureTrustPassword).Password
$env:FITNET_TLS_TRUSTSTORE = ".\secure-transfer\certs\truststore.p12"
java "-Dsecure.transfer.protocol=2" -cp "gateway/target/classes;secure-transfer/target/classes" com.fitnet.gateway.GatewayServer
```

Open **Terminal C** in the repository's `frontend` directory. Run `npm install`
once for a fresh checkout, then start Next.js:

```powershell
npm install
npm run dev
```

Visit [http://localhost:3000/files](http://localhost:3000/files). The default
Gateway is `http://localhost:8080`; the default TLS server is on port `5004`.
The two Java terminals print their listening addresses. From another PowerShell
window, verify the TCP listeners:

```powershell
Test-NetConnection 127.0.0.1 -Port 5004
Test-NetConnection 127.0.0.1 -Port 8080
```

`TcpTestSucceeded` should be `True` for both. This checks reachability. To also
exercise the TLS handshake and application protocol directly, open another
PowerShell terminal in `backend`, set the truststore environment variables as
in Terminal B, and run:

```powershell
java "-Dsecure.transfer.protocol=2" -cp "secure-transfer/target/classes" com.fitnet.transfer.SecureTransferClient list
```

The client verifies the server certificate and hostname, then prints the file
list returned over the TLS connection. The frontend upload and download below
also traverse the Gateway's `SSLSocket` connection to the `SSLServerSocket`.

### Upload, list, download, and compare hashes

Create a small binary fixture in the current user's temporary directory. The
randomized filename uses characters allowed by the transfer service:

```powershell
$demoBytes = [byte[]]::new(4096)
[System.Random]::new().NextBytes($demoBytes)
$demoFile = Join-Path $env:TEMP ("fitnet-demo-" + [guid]::NewGuid().ToString("N") + ".bin")
[System.IO.File]::WriteAllBytes($demoFile, $demoBytes)
$demoFile
```

On the `/files` page:

1. Select the printed `$demoFile` path and choose **Upload securely**.
2. Point out the success notice and its SHA-256 value.
3. Confirm the file appears under **Available files** with its size and shortened
   hash. The full hash is available in the hash's tooltip.
4. Choose **Download** for that row. Save the browser download in the current
   user's `Downloads` folder.

Compare the downloaded file with the original in the same PowerShell session.
The downloaded name matches the original fixture name:

```powershell
$downloadedFile = Join-Path (Join-Path $env:USERPROFILE "Downloads") (Split-Path -Leaf $demoFile)
Get-FileHash -LiteralPath $demoFile -Algorithm SHA256
Get-FileHash -LiteralPath $downloadedFile -Algorithm SHA256
(Get-FileHash -LiteralPath $demoFile -Algorithm SHA256).Hash -eq (Get-FileHash -LiteralPath $downloadedFile -Algorithm SHA256).Hash
```

The final expression should be `True`. The TLS client also verifies the
downloaded bytes before the Gateway sends them to the browser.

### Demonstrate validation and error responses

The frontend checks unsafe names and files over its 100 MiB limit when you
submit an upload, before sending them to the Gateway. Use the Gateway API
directly to demonstrate its HTTP status codes. The following commands use
Windows `curl.exe` and the same endpoints listed in
[Gateway API](#gateway-api).

**Invalid filename — expected HTTP 400:**

```powershell
curl.exe --silent --show-error --include --request POST `
  --header "X-Filename: ../invalid.bin" `
  --header "Content-Type: application/octet-stream" `
  --header "Content-Length: 0" `
  http://localhost:8080/api/files
```

The Gateway rejects the name before reading the request body or creating an
upload temporary file.

**File over the presentation server limit — expected HTTP 413:**

Create a 1 MiB + 1 byte fixture. The running presentation server was started
with a 1 MiB limit; the browser's 100 MiB check allows this fixture to reach the
Gateway and TLS server.

```powershell
$oversizeFile = Join-Path $env:TEMP ("fitnet-oversize-" + [guid]::NewGuid().ToString("N") + ".bin")
$oversizeStream = [System.IO.File]::Open($oversizeFile, [System.IO.FileMode]::CreateNew)
try { $oversizeStream.SetLength(1048577) } finally { $oversizeStream.Dispose() }
$oversizeFile
```

Select the printed `$oversizeFile` path in the UI and choose **Upload securely**
to send it through the Gateway and display the server's size-limit rejection in
the alert area. To show the exact HTTP response status, send the same fixture
through the Gateway:

```powershell
curl.exe --silent --show-error --include --request POST `
  --header "X-Filename: oversize-demo.bin" `
  --header "Content-Type: application/octet-stream" `
  --data-binary "@$oversizeFile" http://localhost:8080/api/files
```

The server rejects the configured size limit; the Gateway returns HTTP 413.
The 1 MiB limit is only for this presentation run. Restart the server without
the `secure.transfer.max.bytes` override to restore its normal 100 MiB limit.

**Missing file — expected HTTP 404:**

```powershell
curl.exe --silent --show-error --include `
  http://localhost:8080/api/files/no-such-demo-file.bin
```

The response body contains a JSON `error` field. The frontend displays Gateway
errors in its alert area; the Java services also return generic errors for
unexpected downstream I/O failures.

### What to explain during the presentation

- **TLS purpose:** TLS encrypts and authenticates the Gateway-to-file-server
  connection. This local demo's browser-to-Gateway hop uses HTTP; it is not
  end-to-end TLS. The Gateway binds to loopback by default.
- **Socket roles:** `SSLServerSocket` listens on TCP port 5004 and accepts TLS
  connections. `SSLSocket` performs the client handshake and sends one transfer
  request on that encrypted connection.
- **Trust:** the server keystore contains its private key and certificate. The
  client truststore contains the server's public certificate. The client
  validates the certificate chain and hostname; this sample does not use
  client-certificate authentication or trust-all validation.
- **Integrity:** SHA-256 detects content changes. During upload, the client
  sends the expected digest and the server compares it with the digest of the
  received bytes before committing the file. During download, the client
  compares the received bytes with the server's digest before moving the
  temporary file into place. SHA-256 is an integrity check, not encryption.
- **Binary streaming:** content is copied as bytes in fixed-size buffers. The
  protocol carries a byte length and digest, so arbitrary binary content is not
  decoded as text.
- **Metadata:** `FileMetadata(fileName, size, sha256)` is preserved through the
  Java client and Gateway JSON responses. Download responses also carry
  `X-File-SHA256`.
- **Protocol compatibility:** V1 remains the default for existing clients. V2
  is opt-in with `"-Dsecure.transfer.protocol=2"`; the server accepts both
  versions. V2 uses a versioned binary header and length-prefixed UTF-8 metadata.
- **Gateway path:** the browser sends HTTP requests to the Java Gateway on port
  8080. The Gateway stages uploads/downloads and calls `SecureTransferClient`
  over TLS; the browser cannot open a Java `SSLSocket` directly.
- **Failure safety:** filenames and sizes are checked, path separators are
  rejected, temporary upload/download files are removed on failure, and files
  are committed only after integrity checks pass. The Gateway reports invalid
  upload names as 400, missing files as 404, size-limit rejections as 413, and
  other downstream service failures as 502.

### Tests and likely questions

Run the module and Gateway integration suite from `backend`:

```powershell
mvn -pl gateway -am test
```

`SecureTransferTest` covers TLS handshakes, trusted/untrusted certificates,
protocol compatibility, binary transfers, hash mismatch and interruption
handling. `TlsContextsTest` checks PKCS12 loading. `GatewayFileTransferTest`
exercises HTTP upload/list/download through the Gateway and a real local TLS
server, including V1 and V2, metadata hashes, invalid names, size rejection,
missing files, and CORS preflight. Run the focused handshake test with:

```powershell
mvn -pl secure-transfer "-Dtest=SecureTransferTest#tlsHandshakeCompletesAndServerRespondsOverEncryptedSocket" test
```

| Lecturer question | Short answer |
| --- | --- |
| Does TLS encrypt the file bytes? | Yes, on the Gateway-to-file-server TLS connection. The browser-to-Gateway local demo hop uses HTTP. |
| Does the truststore contain the private key? | No. The private key stays in the server keystore; the truststore contains the server's public certificate. |
| Does SHA-256 encrypt or prove who sent the file? | No. It detects content mismatch; TLS provides encryption and server authentication for the TLS hop. |
| What happens if a hash does not match? | The transfer is rejected, the incomplete temporary file is removed, and an existing download destination remains intact. |
| Why not send file bytes as JSON or text? | Arbitrary binary values are not valid text. The protocol streams bytes and carries metadata separately. |
| Why keep V1? | Existing V1 clients remain compatible. V2 can be selected explicitly while the server accepts both. |
| Is this mutual TLS? | No. The Gateway authenticates the TLS server using its truststore; the Gateway does not present a client certificate. |

### Troubleshooting

- **Keystore/truststore not found or password rejected:** check the store path
  and password variables in the terminal that launched that Java process.
  PowerShell variables do not carry over to another terminal.
- **The certificate script says files already exist:** keep those files and
  select a new `FITNET_TLS_CERT_DIR` for a fresh development certificate set;
  the script deliberately refuses to overwrite stores.
- **TLS handshake or hostname verification fails:** start the TLS server first,
  confirm the Gateway truststore imports that server's certificate, use the
  matching truststore password, and connect to `localhost` with the included
  development certificate.
- **Connection refused:** verify ports 5004 and 8080, start the TLS server
  before the Gateway, and check that no other process already owns either port.
- **The browser cannot call the Gateway:** use the default origins
  `http://localhost:3000` and `http://localhost:8080`, or configure
  `NEXT_PUBLIC_GATEWAY_BASE_URL` and the exact matching
  `gateway.frontend.origin` described in the [frontend instructions](../../frontend/src/features/secure-transfer/README.md).
- **Unexpected 413 in the presentation:** confirm the server was started with
  the temporary 1 MiB override only for the size-rejection demonstration. The
  normal server default is 100 MiB.

Do not expose this demo Gateway to an untrusted network: it has no user
authentication, and its browser-facing local endpoint uses HTTP. Use HTTPS in
front of the Gateway for a network deployment.

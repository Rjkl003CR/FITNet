$ErrorActionPreference = "Stop"
$certDirectory = if ([string]::IsNullOrWhiteSpace($env:FITNET_TLS_CERT_DIR)) {
    Join-Path (Split-Path -Parent $PSScriptRoot) "certs"
} else {
    [System.IO.Path]::GetFullPath($env:FITNET_TLS_CERT_DIR)
}
New-Item -ItemType Directory -Force -Path $certDirectory | Out-Null
$serverStore = Join-Path $certDirectory "server.p12"
$publicCertificate = Join-Path $certDirectory "server.cer"
$trustStore = Join-Path $certDirectory "truststore.p12"

if ([string]::IsNullOrWhiteSpace($env:FITNET_TLS_KEYSTORE_PASSWORD) -or
    [string]::IsNullOrWhiteSpace($env:FITNET_TLS_TRUSTSTORE_PASSWORD)) {
    throw "Set FITNET_TLS_KEYSTORE_PASSWORD and FITNET_TLS_TRUSTSTORE_PASSWORD before running this script."
}
if ($env:FITNET_TLS_KEYSTORE_PASSWORD.Length -lt 12 -or $env:FITNET_TLS_TRUSTSTORE_PASSWORD.Length -lt 12) {
    throw "Use passwords of at least 12 characters for local development stores."
}

$existingFiles = @($serverStore, $publicCertificate, $trustStore) | Where-Object { Test-Path -LiteralPath $_ }
if ($existingFiles.Count -gt 0) {
    throw "TLS files already exist in $certDirectory. Back them up and remove them manually before generating a replacement."
}

$keytoolCommand = Get-Command keytool -ErrorAction SilentlyContinue
$keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME "bin/keytool.exe" } else { $null }
if (-not $keytool -or -not (Test-Path -LiteralPath $keytool)) {
    if (-not $keytoolCommand) { throw "Install a JDK and ensure keytool is on PATH" }
    $keytool = $keytoolCommand.Source
}

& $keytool -genkeypair -alias fitnet-server -keyalg RSA -keysize 3072 -validity 365 `
    -dname "CN=localhost, OU=FITNet Development, O=FITNet, L=Colombo, C=LK" `
    -ext "SAN=dns:localhost,ip:127.0.0.1" -storetype PKCS12 `
    -keystore $serverStore -storepass:env FITNET_TLS_KEYSTORE_PASSWORD `
    -keypass:env FITNET_TLS_KEYSTORE_PASSWORD -noprompt
if ($LASTEXITCODE -ne 0) { throw "Could not create the server keystore" }

& $keytool -exportcert -alias fitnet-server -keystore $serverStore `
    -storepass:env FITNET_TLS_KEYSTORE_PASSWORD `
    -file $publicCertificate
if ($LASTEXITCODE -ne 0) { throw "Could not export the server certificate" }

& $keytool -importcert -alias fitnet-server -file $publicCertificate -storetype PKCS12 `
    -keystore $trustStore -storepass:env FITNET_TLS_TRUSTSTORE_PASSWORD -noprompt
if ($LASTEXITCODE -ne 0) { throw "Could not create the client truststore" }

Write-Host "Created development TLS material in $certDirectory"
Write-Host "Server keystore: $serverStore"
Write-Host "Client truststore: $trustStore"
Write-Host "The private key remains in the server keystore; only the public certificate was exported."

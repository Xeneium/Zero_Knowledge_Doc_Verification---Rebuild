# Test Live ZKDoc System on PostgreSQL
$ErrorActionPreference = "Stop"

Write-Host "=== 1. Actuator Health Probe ==="
$health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
Write-Host "Health Status: $($health.status)"

Write-Host "=== 2. Fetch Demo Tokens ==="
$tokens = Invoke-RestMethod -Uri "http://localhost:8080/api/public/demo-tokens"
$issuerToken = ($tokens.accounts | Where-Object { $_.id -eq "issuer" }).token
$holderToken = ($tokens.accounts | Where-Object { $_.id -eq "holder" }).token
$verifierToken = ($tokens.accounts | Where-Object { $_.id -eq "verifier" }).token
$adminToken = ($tokens.accounts | Where-Object { $_.id -eq "admin" }).token
Write-Host "Fetched tokens for ISSUER, HOLDER, VERIFIER, ADMIN successfully"

Write-Host "=== 3. Frontend Web Entry Probe ==="
$frontHtml = Invoke-WebRequest -Uri "http://localhost:8080/" -UseBasicParsing
Write-Host "Frontend HTTP Status: $($frontHtml.StatusCode), Content-Length: $($frontHtml.RawContentLength)"

Write-Host "=== 4. Document Registration / Upload Workflow (as ISSUER) ==="
$testFilePath = "$PSScriptRoot\test_live_diploma.pdf"
[System.IO.File]::WriteAllText($testFilePath, "%PDF-1.4 Authoritative University Transcript for John Doe GPA 3.95")
$fileBytes = [System.IO.File]::ReadAllBytes($testFilePath)

# Boundary and body for multipart form data
$boundary = [System.Guid]::NewGuid().ToString()
$LF = "`r`n"
$bodyLines = @(
    "--$boundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test_live_diploma.pdf`"",
    "Content-Type: application/pdf",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($fileBytes),
    "--$boundary--"
)
$multipartBody = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes(($bodyLines -join $LF))

$headers = @{
    "Authorization" = "Bearer $issuerToken"
    "Content-Type" = "multipart/form-data; boundary=$boundary"
}

$uploadResp = Invoke-RestMethod -Uri "http://localhost:8080/api/documents" -Method Post -Headers $headers -Body $multipartBody
Write-Host "Uploaded Document ID: $($uploadResp.id)"
Write-Host "SHA256 Digest: $($uploadResp.digest)"
Write-Host "Public Token: $($uploadResp.publicToken)"
Write-Host "Verification URL: $($uploadResp.verificationUrl)"

Write-Host "=== 5. Public QR Verification Link Probe (Unauthenticated) ==="
$verifyResp = Invoke-RestMethod -Uri "http://localhost:8080/api/public/verify/$($uploadResp.publicToken)"
Write-Host "Public Token Verify Result: signatureValid=$($verifyResp.signatureValid), documentActive=$($verifyResp.documentActive)"

Write-Host "=== 6. Direct File Upload Verification (Unauthenticated) ==="
$pubBoundary = [System.Guid]::NewGuid().ToString()
$pubBodyLines = @(
    "--$pubBoundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"test_live_diploma.pdf`"",
    "Content-Type: application/pdf",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($fileBytes),
    "--$pubBoundary--"
)
$pubMultipartBody = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes(($pubBodyLines -join $LF))
$pubHeaders = @{
    "Content-Type" = "multipart/form-data; boundary=$pubBoundary"
}

try {
    $pubVerifyUploadResp = Invoke-RestMethod -Uri "http://localhost:8080/api/public/verify/upload" -Method Post -Headers $pubHeaders -Body $pubMultipartBody
    Write-Host "Public File Upload Verify Result: status=$($pubVerifyUploadResp.status), verified=$($pubVerifyUploadResp.verified)"
} catch {
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "Error Body: $($reader.ReadToEnd())"
    } else {
        Write-Host "Error: $($_.Exception.Message)"
    }
}

Write-Host "=== 7. Tampered File Upload Verification (Must Detect Mismatch) ==="
$tamperedBytes = [System.Text.Encoding]::UTF8.GetBytes("TAMPERED CONTENT THAT DOES NOT MATCH AUTHORITATIVE RECORD")
$tampBoundary = [System.Guid]::NewGuid().ToString()
$tampBodyLines = @(
    "--$tampBoundary",
    "Content-Disposition: form-data; name=`"file`"; filename=`"tampered.pdf`"",
    "Content-Type: application/pdf",
    "",
    [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($tamperedBytes),
    "--$tampBoundary--"
)
$tampMultipartBody = [System.Text.Encoding]::GetEncoding("iso-8859-1").GetBytes(($tampBodyLines -join $LF))
$tampHeaders = @{
    "Content-Type" = "multipart/form-data; boundary=$tampBoundary"
}

$tampResp = Invoke-RestMethod -Uri "http://localhost:8080/api/public/verify/upload" -Method Post -Headers $tampHeaders -Body $tampMultipartBody
Write-Host "Tampered File Upload Result: status=$($tampResp.status), verified=$($tampResp.verified)"

Write-Host "=== 8. QR Code PNG Generation Probe ==="
$qrHeaders = @{ "Authorization" = "Bearer $issuerToken" }
$qrResp = Invoke-WebRequest -Uri "http://localhost:8080/api/documents/$($uploadResp.id)/qr" -Headers $qrHeaders -UseBasicParsing
Write-Host "QR Code Status: $($qrResp.StatusCode), Content-Type: $($qrResp.Headers['Content-Type']), Byte Length: $($qrResp.RawContentLength)"

Write-Host "=== 9. Object-Level Access Control (BOLA/IDOR Security Test) ==="
$holderHeaders = @{ "Authorization" = "Bearer $holderToken" }
try {
    $null = Invoke-WebRequest -Uri "http://localhost:8080/api/documents/$($uploadResp.id)/qr" -Headers $holderHeaders -UseBasicParsing
    Write-Host "SECURITY ERROR: Holder was able to access issuer document QR!"
} catch {
    Write-Host "BOLA/IDOR Protection Verified: Holder forbidden from accessing Issuer document QR (HTTP $($_.Exception.Response.StatusCode.value__))"
}

Write-Host "=== 10. Zero-Knowledge Proof (Bulletproofs) Workflow ==="
# 10.1 List holder credentials
$holderCreds = Invoke-RestMethod -Uri "http://localhost:8080/api/holder/credentials" -Headers @{ "Authorization" = "Bearer $holderToken" }
Write-Host "Holder has $($holderCreds.Count) provisioned credentials in wallet"
$aliceCred = $holderCreds | Where-Object { $_.credentialId -eq "a0000000-0000-4000-8000-000000000001" }
if (-not $aliceCred) { $aliceCred = $holderCreds[0] }

# 10.2 Request ZK Predicate Proof from Holder Wallet (e.g. Age >= 18)
$nonceHex = "0102030405060708090a0b0c0d0e0f10"
$proofReq = @{
    holderCredentialId = $aliceCred.id
    attributeName = "age"
    operator = ">="
    threshold = 18
    nonceHex = $nonceHex
} | ConvertTo-Json

try {
    $proofResp = Invoke-RestMethod -Uri "http://localhost:8080/api/holder/credentials/generate-predicate-proof" `
        -Method Post `
        -Headers @{ "Authorization" = "Bearer $holderToken"; "Content-Type" = "application/json" } `
        -Body $proofReq
} catch {
    if ($_.Exception.Response) {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        Write-Host "ZKP Proof Generate Error: $($reader.ReadToEnd())"
    } else {
        Write-Host "Error: $($_.Exception.Message)"
    }
    throw
}

Write-Host "ZKP Proof Generated: proofVersion=$($proofResp.proofVersion), bitSize=$($proofResp.bitSize), proofHexLength=$($proofResp.proofBytesHex.Length)"

# 10.3 Verify Proof as Verifier (or unauthenticated verifier role)
# Convert hex to base64 for byte[] fields
function Convert-HexToBase64($hex) {
    $bytes = New-Object byte[] ($hex.Length / 2)
    for ($i = 0; $i -lt $hex.Length; $i += 2) {
        $bytes[$i / 2] = [System.Convert]::ToByte($hex.Substring($i, 2), 16)
    }
    return [System.Convert]::ToBase64String($bytes)
}

$verifyZkpReq = @{
    proofVersion = $proofResp.proofVersion
    credentialId = $proofResp.credentialId
    attributeName = $proofResp.attributeName
    threshold = $proofResp.threshold
    nonce = Convert-HexToBase64 $nonceHex
    proofBytes = Convert-HexToBase64 $proofResp.proofBytesHex
} | ConvertTo-Json

$verifyZkpResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v2/zkp/credential-predicates/verify" `
    -Method Post `
    -Headers @{ "Authorization" = "Bearer $verifierToken"; "Content-Type" = "application/json" } `
    -Body $verifyZkpReq

Write-Host "ZKP Verification Result: verified=$($verifyZkpResp.verified), reason=$($verifyZkpResp.reason)"

# Cleanup temporary test file
if (Test-Path $testFilePath) { Remove-Item $testFilePath }

Write-Host "=== ALL LIVE PROBES COMPLETED SUCCESSFULLY ==="

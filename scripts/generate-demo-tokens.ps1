<#
.SYNOPSIS
    Fetches and displays active cryptographic demo Bearer JWT tokens for all evaluation roles.
.DESCRIPTION
    Queries the ZKDoc platform demo tokens endpoint (http://localhost:8080/api/public/demo-tokens)
    and formats the credentials for instant copying into CLI commands, curl requests, or Postman.
#>

[CmdletBinding()]
param (
    [string]$BaseUrl = "http://localhost:8080"
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   ZKDoc Platform - Demonstration Bearer JWT Token Utility       " -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

$endpoint = "$BaseUrl/api/public/demo-tokens"

try {
    Write-Host "[*] Querying public demo token endpoint: $endpoint..." -ForegroundColor Gray
    $response = Invoke-RestMethod -Uri $endpoint -Method Get -TimeoutSec 5 -Headers @{ "Accept" = "application/json" }
    
    if (-not $response.accounts -or $response.accounts.Count -eq 0) {
        Write-Warning "Endpoint responded, but returned no accounts. Ensure 'zkdoc.demo.seed-enabled' is true."
        exit 1
    }

    Write-Host "[+] Successfully retrieved $($response.accounts.Count) active demo tokens.`n" -ForegroundColor Green

    foreach ($acc in $response.accounts) {
        $color = switch ($acc.role) {
            "ADMIN"    { "Red" }
            "ISSUER"   { "Cyan" }
            "HOLDER"   { "Green" }
            "VERIFIER" { "Yellow" }
            Default    { "White" }
        }

        Write-Host "-----------------------------------------------------------------" -ForegroundColor DarkGray
        Write-Host "ROLE:        " -NoNewline; Write-Host "$($acc.role)" -ForegroundColor $color
        Write-Host "USERNAME:    $($acc.username)" -ForegroundColor White
        Write-Host "SUBJECT:     $($acc.subject)" -ForegroundColor Gray
        Write-Host "DESCRIPTION: $($acc.description)" -ForegroundColor Gray
        Write-Host "TOKEN:       " -ForegroundColor DarkGray
        Write-Host "$($acc.token)" -ForegroundColor White
        Write-Host ""
    }

    Write-Host "-----------------------------------------------------------------" -ForegroundColor DarkGray
    Write-Host "[*] Quick-Paste Helper for cURL / HTTPie:" -ForegroundColor Cyan
    $holderToken = ($response.accounts | Where-Object { $_.role -eq 'HOLDER' }).token
    Write-Host "curl -X GET $BaseUrl/api/holder/credentials \" -ForegroundColor White
    Write-Host "     -H `"Authorization: Bearer $holderToken`"" -ForegroundColor White
    Write-Host ""

} catch {
    Write-Host "[!] Could not connect to ZKDoc server at $endpoint" -ForegroundColor Red
    Write-Host "    Make sure the application is running with demo seed enabled:" -ForegroundColor Yellow
    Write-Host "    .\mvnw.cmd spring-boot:run -pl zkdoc-api -Dzkdoc.demo.seed-enabled=true" -ForegroundColor White
    Write-Host "    or run the automated setup script:" -ForegroundColor Yellow
    Write-Host "    .\scripts\demo-setup.ps1" -ForegroundColor White
    Write-Host ""
    exit 1
}

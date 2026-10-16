<#
.SYNOPSIS
    Automated demo environment launcher for the ZKDoc Platform.
.DESCRIPTION
    Validates prerequisites (Java 21, Python/Fixtures), initializes required directories,
    ensures sample PDF fixtures exist, and starts the Spring Boot platform with demo seeding enabled.
#>

[CmdletBinding()]
param (
    [switch]$SkipBuild,
    [switch]$Docker
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   ZKDoc Platform - Demonstration Setup & Launch Assistant       " -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

$RootPath = (Get-Item $PSScriptRoot).Parent.FullName

# 1. Check Java 21
Write-Host "[1/4] Checking Java 21 environment..." -ForegroundColor Yellow
try {
    $javaVerOutput = java -version 2>&1 | Out-String
    if ($javaVerOutput -match 'version "(\d+)') {
        $majorVer = [int]$matches[1]
        if ($majorVer -lt 21) {
            Write-Error "Detected Java version $majorVer. Java 21 LTS is required."
            exit 1
        }
        Write-Host "      Java 21 detected: $javaVerOutput.Trim().Split(`"`n`")[0]" -ForegroundColor Green
    } else {
        Write-Warning "      Could not parse Java version. Continuing..."
    }
} catch {
    Write-Error "Java 21 is not found on PATH. Please install OpenJDK 21."
    exit 1
}

# 2. Check & Generate PDF Fixtures
Write-Host "[2/4] Verifying synthetic test PDF fixtures..." -ForegroundColor Yellow
$FixturesDir = Join-Path $RootPath "demo\fixtures"
$ValidDoc = Join-Path $FixturesDir "demo-degree-diploma.pdf"
$TamperedDoc = Join-Path $FixturesDir "demo-degree-diploma-tampered.pdf"
$CertDoc = Join-Path $FixturesDir "demo-training-certificate.pdf"

if (-not (Test-Path $ValidDoc) -or -not (Test-Path $TamperedDoc) -or -not (Test-Path $CertDoc)) {
    Write-Host "      Generating synthetic PDF test documents using Python..." -ForegroundColor Gray
    try {
        python (Join-Path $FixturesDir "generate_pdfs.py")
        Write-Host "      Fixtures successfully generated." -ForegroundColor Green
    } catch {
        Write-Warning "      Could not generate fixtures with Python. If Python is not installed, please ensure fixtures exist."
    }
} else {
    Write-Host "      Synthetic fixtures verified:" -ForegroundColor Green
    Write-Host "      - $(Split-Path $ValidDoc -Leaf) (Valid credential)" -ForegroundColor Gray
    Write-Host "      - $(Split-Path $TamperedDoc -Leaf) (Tampered GPA variant)" -ForegroundColor Gray
    Write-Host "      - $(Split-Path $CertDoc -Leaf) (Training certificate)" -ForegroundColor Gray
}

# 3. Create Storage Directories
Write-Host "[3/4] Ensuring storage directories..." -ForegroundColor Yellow
$ArtifactsDir = Join-Path $RootPath "data\artifacts"
if (-not (Test-Path $ArtifactsDir)) {
    New-Item -ItemType Directory -Force -Path $ArtifactsDir | Out-Null
}
Write-Host "      Storage initialized at: $ArtifactsDir" -ForegroundColor Green

# 4. Display Quick-Reference Guide
Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   DEMO ACCOUNTS READY (Seeded automatically on startup)        " -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  Role      | Username        | Purpose / Capabilities" -ForegroundColor White
Write-Host "  ----------+-----------------+---------------------------------" -ForegroundColor DarkGray
Write-Host "  ADMIN     | admin-operator  | Key registry, audit log chain, metrics" -ForegroundColor Red
Write-Host "  ISSUER    | issuer-agent    | Upload doc, sign SHA-256, issue credentials" -ForegroundColor Cyan
Write-Host "  HOLDER    | alice-chen      | Wallet, Bulletproof ZKP range proofs" -ForegroundColor Green
Write-Host "  VERIFIER  | verifier-auditor| Mode A/B verification, verify predicates" -ForegroundColor Yellow
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Web Application:   http://localhost:8080" -ForegroundColor White
Write-Host "  Quick-Connect:     http://localhost:8080/login" -ForegroundColor White
Write-Host "  Public Verify:     http://localhost:8080/verify" -ForegroundColor White
Write-Host "  API Demo Tokens:   http://localhost:8080/api/public/demo-tokens" -ForegroundColor White
Write-Host "  Health Actuator:   http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

if ($Docker) {
    Write-Host "[*] Launching platform via Docker Compose..." -ForegroundColor Green
    docker compose up --build
} else {
    Write-Host "[*] Launching ZKDoc Platform with Demo Seeding via Maven..." -ForegroundColor Green
    $mvnCmd = Join-Path $RootPath "mvnw.cmd"
    & $mvnCmd spring-boot:run -pl zkdoc-api -Dspring-boot.run.arguments="--zkdoc.demo.seed-enabled=true"
}

# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Production Restoration Script (PowerShell for Windows Environments)
# Phase 22 - Backup, Recovery & Deployment Hardening
# ==============================================================================
[CmdletBinding()]
param (
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$BackupArchive,
    [string]$TargetDb = "zkdoc_restore_test",
    [string]$TargetArtifactsDir = ".\data\artifacts_restored",
    [string]$TargetSqlitePath = ".\data\zkdoc_restored.db",
    [switch]$ForceOverwriteTarget
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

if (-not (Test-Path $BackupArchive)) {
    throw "Backup archive file not found: $BackupArchive"
}

# Production safety check
$ProdDbName = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "zkdoc" }
if ($TargetDb -eq $ProdDbName -and -not $ForceOverwriteTarget) {
    throw "CRITICAL SAFEGUARD VIOLATION: Target database '$TargetDb' is the production database! Overwrite requires -ForceOverwriteTarget."
}

$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmssZ")
$StageDir = Join-Path $env:TEMP "zkdoc_restore_$Timestamp"

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " Starting ZKDoc Platform Restoration" -ForegroundColor Cyan
Write-Host " Backup Archive:    $BackupArchive" -ForegroundColor Cyan
Write-Host " Target Database:   $TargetDb" -ForegroundColor Cyan
Write-Host " Target Artifacts:  $TargetArtifactsDir" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

try {
    if (Test-Path $StageDir) {
        Remove-Item -Path $StageDir -Recurse -Force
    }
    New-Item -ItemType Directory -Path $StageDir -Force | Out-Null

    # 1. Unpack archive
    Write-Host "[*] Step 1: Unpacking and verifying backup bundle..." -ForegroundColor Yellow
    Expand-Archive -Path $BackupArchive -DestinationPath $StageDir -Force

    # 2. Validate manifest and checksums
    Write-Host "[*] Step 2: Verifying cryptographic checksums from manifest..." -ForegroundColor Yellow
    $ManifestPath = Join-Path $StageDir "manifest.json"
    if (-not (Test-Path $ManifestPath)) {
        throw "Backup manifest.json missing! Archive may be corrupted or tampered."
    }

    $Manifest = Get-Content -Path $ManifestPath -Raw | ConvertFrom-Json
    $DbFileObj = Get-ChildItem -Path $StageDir -Filter "database.*" | Select-Object -First 1

    $ActualDbHash = (Get-FileHash -Path $DbFileObj.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $ExpectedDbHash = $Manifest.database.sha256.ToLowerInvariant()

    if ($ActualDbHash -ne $ExpectedDbHash) {
        throw "CRITICAL CORRUPTION: Database hash mismatch! Expected: $ExpectedDbHash, Actual: $ActualDbHash"
    }
    Write-Host "[+] Database checksum verified: $ActualDbHash" -ForegroundColor Green

    $ArtifactZip = Join-Path $StageDir "artifacts.zip"
    if (Test-Path $ArtifactZip) {
        $ActualArtHash = (Get-FileHash -Path $ArtifactZip -Algorithm SHA256).Hash.ToLowerInvariant()
        $ExpectedArtHash = $Manifest.artifacts.sha256.ToLowerInvariant()
        if ($ActualArtHash -ne $ExpectedArtHash) {
            throw "CRITICAL CORRUPTION: Artifact archive hash mismatch! Expected: $ExpectedArtHash, Actual: $ActualArtHash"
        }
        Write-Host "[+] Artifacts archive checksum verified: $ActualArtHash" -ForegroundColor Green
    }

    # 3. Restore Database
    Write-Host "[*] Step 3: Restoring database to target: $TargetDb..." -ForegroundColor Yellow
    if ($Manifest.databaseType -eq "postgresql") {
        $DbUser = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "zkdoc_app" }
        $DbHost = if ($env:POSTGRES_HOST) { $env:POSTGRES_HOST } else { "localhost" }
        $DbPort = if ($env:POSTGRES_PORT) { $env:POSTGRES_PORT } else { 5432 }
        $env:PGPASSWORD = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "" }
        pg_restore -h $DbHost -p $DbPort -U $DbUser -d $TargetDb --clean --if-exists --no-owner --no-privileges $DbFileObj.FullName
        $env:PGPASSWORD = $null
        Write-Host "[+] PostgreSQL database restored into '$TargetDb'." -ForegroundColor Green
    } else {
        $SqliteParent = Split-Path -Parent $TargetSqlitePath
        if ($SqliteParent -and -not (Test-Path $SqliteParent)) {
            New-Item -ItemType Directory -Path $SqliteParent -Force | Out-Null
        }
        Copy-Item -Path $DbFileObj.FullName -Destination $TargetSqlitePath -Force
        Write-Host "[+] SQLite database restored to '$TargetSqlitePath'." -ForegroundColor Green
    }

    # 4. Restore Artifacts
    Write-Host "[*] Step 4: Restoring document artifacts to: $TargetArtifactsDir..." -ForegroundColor Yellow
    if (-not (Test-Path $TargetArtifactsDir)) {
        New-Item -ItemType Directory -Path $TargetArtifactsDir -Force | Out-Null
    }
    if (Test-Path $ArtifactZip) {
        Expand-Archive -Path $ArtifactZip -DestinationPath $TargetArtifactsDir -Force
        # Clean any placeholder
        $EmptyPlaceholder = Join-Path $TargetArtifactsDir ".empty"
        if (Test-Path $EmptyPlaceholder) {
            Remove-Item $EmptyPlaceholder -Force
        }
        Write-Host "[+] Document artifacts extracted." -ForegroundColor Green
    }

    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host " [+] RESTORATION EXECUTED SUCCESSFULLY" -ForegroundColor Green
    Write-Host " Next Steps for Operator Verification:" -ForegroundColor Green
    Write-Host " 1. Run recovery reconciliation endpoint: POST /api/admin/recovery/reconcile" -ForegroundColor Green
    Write-Host " 2. Verify schema, artifacts, key registry, and audit hash chains." -ForegroundColor Green
    Write-Host "==============================================================================" -ForegroundColor Green
}
finally {
    if (Test-Path $StageDir) {
        Remove-Item -Path $StageDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

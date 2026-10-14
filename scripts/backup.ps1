# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Production Backup Script (PowerShell for Windows Environments)
# Phase 22 - Backup, Recovery & Deployment Hardening
# ==============================================================================
[CmdletBinding()]
param (
    [string]$BackupDir = ".\backups",
    [string]$ArtifactsDir = ".\data\artifacts",
    [int]$RetentionDays = 7,
    [ValidateSet("postgresql", "sqlite")]
    [string]$DatabaseType = "sqlite",
    [string]$DbName = "zkdoc",
    [string]$DbUser = "zkdoc_app",
    [string]$DbHost = "localhost",
    [int]$DbPort = 5432,
    [string]$SqliteDbPath = ".\data\zkdoc.db"
)

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

$Timestamp = (Get-Date).ToUniversalTime().ToString("yyyyMMdd_HHmmssZ")
$BackupId = "zkdoc_backup_$Timestamp"
$TempStaging = Join-Path $env:TEMP $BackupId

Write-Host "==============================================================================" -ForegroundColor Cyan
Write-Host " Starting ZKDoc Platform Backup: $BackupId" -ForegroundColor Cyan
Write-Host " Timestamp (UTC): $Timestamp" -ForegroundColor Cyan
Write-Host " Database Type:   $DatabaseType" -ForegroundColor Cyan
Write-Host "==============================================================================" -ForegroundColor Cyan

try {
    # Create directories
    if (-not (Test-Path $BackupDir)) {
        New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
    }
    if (Test-Path $TempStaging) {
        Remove-Item -Path $TempStaging -Recurse -Force
    }
    New-Item -ItemType Directory -Path $TempStaging -Force | Out-Null

    # 1. Database Backup
    Write-Host "[*] Step 1: Performing consistent database dump..." -ForegroundColor Yellow
    if ($DatabaseType -eq "postgresql") {
        $DumpFile = Join-Path $TempStaging "database.dump"
        $env:PGPASSWORD = if ($env:POSTGRES_PASSWORD) { $env:POSTGRES_PASSWORD } else { "" }
        pg_dump -h $DbHost -p $DbPort -U $DbUser -d $DbName -Fc --no-owner --no-privileges -f $DumpFile
        $env:PGPASSWORD = $null
        Write-Host "[+] PostgreSQL dump completed: $DumpFile" -ForegroundColor Green
    } else {
        if (-not (Test-Path $SqliteDbPath)) {
            throw "SQLite database file not found at: $SqliteDbPath"
        }
        $SqliteDump = Join-Path $TempStaging "database.sqlite"
        Copy-Item -Path $SqliteDbPath -Destination $SqliteDump -Force
        Write-Host "[+] SQLite database copy completed: $SqliteDump" -ForegroundColor Green
    }

    # 2. Document Artifacts Backup
    Write-Host "[*] Step 2: Archiving document artifacts store..." -ForegroundColor Yellow
    $ArtifactZip = Join-Path $TempStaging "artifacts.zip"
    $ArtifactCount = 0
    if (Test-Path $ArtifactsDir) {
        $Files = Get-ChildItem -Path $ArtifactsDir -File -Recurse
        $ArtifactCount = $Files.Count
        if ($ArtifactCount -gt 0) {
            Compress-Archive -Path "$ArtifactsDir\*" -DestinationPath $ArtifactZip -CompressionLevel Optimal
        } else {
            # Create empty zip
            Set-Content -Path (Join-Path $TempStaging ".empty") -Value ""
            Compress-Archive -Path (Join-Path $TempStaging ".empty") -DestinationPath $ArtifactZip
            Remove-Item (Join-Path $TempStaging ".empty")
        }
    } else {
        Set-Content -Path (Join-Path $TempStaging ".empty") -Value ""
        Compress-Archive -Path (Join-Path $TempStaging ".empty") -DestinationPath $ArtifactZip
        Remove-Item (Join-Path $TempStaging ".empty")
    }
    Write-Host "[+] Artifact archive created ($ArtifactCount files preserved)." -ForegroundColor Green

    # 3. Private Key Non-Export Safety Boundary Check
    Write-Host "[*] Step 3: Verifying private-key isolation boundary..." -ForegroundColor Yellow
    $LeakCheck = Get-ChildItem -Path $TempStaging -Recurse -File | Select-String -Pattern "BEGIN PRIVATE KEY|BEGIN RSA PRIVATE KEY"
    if ($LeakCheck) {
        throw "CRITICAL SECURITY VIOLATION: Private key material detected in backup staging! Aborting."
    }
    Write-Host "[+] Key isolation verified: No private signing keys detected in backup payload." -ForegroundColor Green

    # 4. Generate SHA-256 Manifest
    Write-Host "[*] Step 4: Generating cryptographic integrity manifest..." -ForegroundColor Yellow
    $DbFileObj = Get-ChildItem -Path $TempStaging -Filter "database.*" | Select-Object -First 1
    $DbHash = (Get-FileHash -Path $DbFileObj.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    $DbSize = $DbFileObj.Length

    $ArtHash = (Get-FileHash -Path $ArtifactZip -Algorithm SHA256).Hash.ToLowerInvariant()
    $ArtSize = (Get-Item $ArtifactZip).Length

    $Manifest = [ordered]@{
        backupId = $BackupId
        timestamp = $Timestamp
        databaseType = $DatabaseType
        database = @{
            filename = $DbFileObj.Name
            sha256 = $DbHash
            sizeBytes = $DbSize
        }
        artifacts = @{
            filename = "artifacts.zip"
            sha256 = $ArtHash
            sizeBytes = $ArtSize
            fileCount = $ArtifactCount
        }
        privateKeyScanPassed = $true
        generator = "zkdoc-backup-ps1-v1"
    }

    $ManifestJsonPath = Join-Path $TempStaging "manifest.json"
    $Manifest | ConvertTo-Json -Depth 5 | Set-Content -Path $ManifestJsonPath -Encoding utf8
    $ManifestHash = (Get-FileHash -Path $ManifestJsonPath -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -Path (Join-Path $TempStaging "manifest.sha256") -Value "$ManifestHash  manifest.json"
    Write-Host "[+] Manifest created with SHA-256: $ManifestHash" -ForegroundColor Green

    # 5. Package Final Backup Bundle
    $FinalArchive = Join-Path $BackupDir "$BackupId.zip"
    Write-Host "[*] Step 5: Packaging final archive: $FinalArchive..." -ForegroundColor Yellow
    Compress-Archive -Path "$TempStaging\*" -DestinationPath $FinalArchive -CompressionLevel Optimal
    $FinalHash = (Get-FileHash -Path $FinalArchive -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -Path "$FinalArchive.sha256" -Value "$FinalHash  $BackupId.zip"

    # 6. Retention Enforcement
    Write-Host "[*] Step 6: Enforcing retention policy ($RetentionDays days)..." -ForegroundColor Yellow
    $CutoffDate = (Get-Date).AddDays(-$RetentionDays)
    $OldBackups = Get-ChildItem -Path $BackupDir -Filter "zkdoc_backup_*.zip*" | Where-Object { $_.CreationTime -lt $CutoffDate }
    $AllBackups = Get-ChildItem -Path $BackupDir -Filter "zkdoc_backup_*.zip"
    if ($AllBackups.Count -gt 1 -and $OldBackups) {
        $OldBackups | Remove-Item -Force
        Write-Host "[+] Cleaned up $($OldBackups.Count) expired backup files." -ForegroundColor Green
    }

    Write-Host "==============================================================================" -ForegroundColor Green
    Write-Host " [+] BACKUP COMPLETED SUCCESSFULLY" -ForegroundColor Green
    Write-Host " Archive:  $FinalArchive" -ForegroundColor Green
    Write-Host " SHA-256:  $FinalHash" -ForegroundColor Green
    Write-Host " Size:     $((Get-Item $FinalArchive).Length) bytes" -ForegroundColor Green
    Write-Host "==============================================================================" -ForegroundColor Green
}
finally {
    if (Test-Path $TempStaging) {
        Remove-Item -Path $TempStaging -Recurse -Force -ErrorAction SilentlyContinue
    }
}

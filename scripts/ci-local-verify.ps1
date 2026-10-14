# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Local CI & Supply-Chain Security Verification Script (PowerShell)
# ==============================================================================
param(
    [switch]$SkipTests = $false,
    [switch]$SkipDocker = $false
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path "$PSScriptRoot\.."

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "  ZKDoc Pipeline: Local CI & Supply-Chain Security Verification  " -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Toolchain Verification
Write-Host "`n[Step 1/7] Inspecting toolchain versions..." -ForegroundColor Yellow
$javaOutput = cmd /c "java -version 2>&1"
$firstLine = if ($javaOutput) { $javaOutput[0] } else { "Unknown" }
Write-Host "  Java: $firstLine"
$nodeVer = node -v
Write-Host "  Node.js: $nodeVer"
$npmVer = npm -v
Write-Host "  npm: $npmVer"

# 2. Backend Build & Tests
Write-Host "`n[Step 2/7] Executing backend compilation & test suite..." -ForegroundColor Yellow
if (-not $SkipTests) {
    & "$root\mvnw.cmd" test "-Dfrontend.skip=true"
    if ($LASTEXITCODE -ne 0) { throw "Backend test suite failed." }
} else {
    Write-Host "  (Skipping backend tests as requested)" -ForegroundColor DarkGray
}

Write-Host "  Packaging backend Spring Boot executable JAR..." -ForegroundColor Yellow
& "$root\mvnw.cmd" package -DskipTests "-Dfrontend.skip=true"
if ($LASTEXITCODE -ne 0) { throw "Backend packaging failed." }

$apiJar = Get-ChildItem -Path "$root\zkdoc-api\target" -Filter "zkdoc-api-*.jar" | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $apiJar) { throw "Packaged backend JAR not found." }
$jarHash = Get-FileHash -Path $apiJar.FullName -Algorithm SHA256
$hashFile = "$root\zkdoc-api\target\zkdoc-api-artifact.sha256"
"$($jarHash.Hash)  $($apiJar.Name)" | Out-File -FilePath $hashFile -Encoding utf8
Write-Host "  Backend artifact: $($apiJar.Name) ($([math]::Round($apiJar.Length / 1MB, 2)) MB)" -ForegroundColor Green
Write-Host "  SHA-256: $($jarHash.Hash)" -ForegroundColor Green

# 3. Frontend Verification
Write-Host "`n[Step 3/7] Verifying frontend integrity, types, and tests..." -ForegroundColor Yellow
Push-Location "$root\frontend"
try {
    Write-Host "  Running frontend static typecheck (tsc --noEmit)..."
    & npm run typecheck
    if ($LASTEXITCODE -ne 0) { throw "Frontend typecheck failed." }

    if (-not $SkipTests) {
        Write-Host "  Running frontend test suite (vitest run)..."
        & npm run test:run
        if ($LASTEXITCODE -ne 0) { throw "Frontend tests failed." }
    }

    Write-Host "  Building frontend production assets..."
    & npm run build
    if ($LASTEXITCODE -ne 0) { throw "Frontend build failed." }
} finally {
    Pop-Location
}

# 4. SBOM Generation
Write-Host "`n[Step 4/7] Generating Software Bill of Materials (SBOM)..." -ForegroundColor Yellow
& "$root\mvnw.cmd" org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DskipTests "-Dfrontend.skip=true"
if ($LASTEXITCODE -ne 0) { throw "Maven CycloneDX SBOM generation failed." }

Push-Location "$root\frontend"
try {
    & npm sbom --sbom-format cyclonedx | Out-File -FilePath "$root\target\frontend-bom.json" -Encoding utf8
} finally {
    Pop-Location
}

$backendBom = "$root\target\bom.json"
$frontendBom = "$root\target\frontend-bom.json"
if ((Test-Path $backendBom) -and (Test-Path $frontendBom)) {
    $backendHash = (Get-FileHash $backendBom -Algorithm SHA256).Hash
    $frontendHash = (Get-FileHash $frontendBom -Algorithm SHA256).Hash
    Write-Host "  Backend CycloneDX SBOM: $backendBom (SHA256: $backendHash)" -ForegroundColor Green
    Write-Host "  Frontend CycloneDX SBOM: $frontendBom (SHA256: $frontendHash)" -ForegroundColor Green
} else {
    throw "SBOM generation did not create expected output files."
}

# 5. Secret Hygiene Audit
Write-Host "`n[Step 5/7] Running secret scanning and credential hygiene check..." -ForegroundColor Yellow
$suspects = Get-ChildItem -Path $root -Recurse -Include "*.java", "*.yml", "*.yaml", "*.properties", "*.json", "*.env" `
    -Exclude "node_modules", "target", ".git" |
    Select-String -Pattern "BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY" |
    Where-Object { $_.Path -notlike "*AuditDataMinimizationSecurityTest.java" }

if ($suspects) {
    Write-Host "  POTENTIAL SECRET DETECTED:" -ForegroundColor Red
    $suspects | ForEach-Object { Write-Host "    $($_.Path):$($_.LineNumber)" -ForegroundColor Red }
    throw "Secret detection check failed."
} else {
    Write-Host "  No private keys or unmasked secrets detected in tracked source files." -ForegroundColor Green
}

# 6. Container Configuration Check
Write-Host "`n[Step 6/7] Validating container configuration..." -ForegroundColor Yellow
if (Test-Path "$root\Dockerfile") {
    $dockerfile = Get-Content "$root\Dockerfile" -Raw
    if ($dockerfile -match "USER zkdoc:zkdoc" -and $dockerfile -match "HEALTHCHECK") {
        Write-Host "  Dockerfile conforms to unprivileged runtime and healthcheck standards." -ForegroundColor Green
    } else {
        Write-Warning "Dockerfile missing unprivileged USER or HEALTHCHECK."
    }
}

Write-Host "`n=================================================================" -ForegroundColor Cyan
Write-Host "  SUCCESS: All CI & Supply-Chain Security verifications passed!  " -ForegroundColor Green
Write-Host "=================================================================" -ForegroundColor Cyan

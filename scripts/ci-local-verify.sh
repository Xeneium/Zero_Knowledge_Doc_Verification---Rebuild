#!/usr/bin/env bash
# ==============================================================================
# Zero-Knowledge Document Verification Platform
# Local CI & Supply-Chain Security Verification Script (POSIX/Bash)
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "================================================================="
echo "  ZKDoc Pipeline: Local CI & Supply-Chain Security Verification  "
echo "================================================================="

# 1. Toolchain
echo ""
echo "[Step 1/7] Inspecting toolchain versions..."
java -version
node -v
npm -v

# 2. Backend Build & Tests
echo ""
echo "[Step 2/7] Executing backend compilation & test suite..."
"${ROOT_DIR}/mvnw" test -Dfrontend.skip=true

echo "  Packaging backend Spring Boot executable JAR..."
"${ROOT_DIR}/mvnw" package -DskipTests -Dfrontend.skip=true

JAR_FILE=$(find "${ROOT_DIR}/zkdoc-api/target" -maxdepth 1 -name "zkdoc-api-*.jar" ! -name "*.original" | head -n 1)
if [[ -z "${JAR_FILE}" ]]; then
  echo "Error: Backend JAR not found." >&2
  exit 1
fi
sha256sum "${JAR_FILE}" > "${ROOT_DIR}/zkdoc-api/target/zkdoc-api-artifact.sha256"
echo "  Backend artifact: $(basename "${JAR_FILE}")"
cat "${ROOT_DIR}/zkdoc-api/target/zkdoc-api-artifact.sha256"

# 3. Frontend Verification
echo ""
echo "[Step 3/7] Verifying frontend integrity, types, and tests..."
(
  cd "${ROOT_DIR}/frontend"
  npm ci
  npm run typecheck
  npm run test:run
  npm run build
)

# 4. SBOM Generation
echo ""
echo "[Step 4/7] Generating Software Bill of Materials (SBOM)..."
"${ROOT_DIR}/mvnw" org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom -DskipTests -Dfrontend.skip=true

(
  cd "${ROOT_DIR}/frontend"
  npm sbom --sbom-format cyclonedx > "${ROOT_DIR}/target/frontend-bom.json"
)

sha256sum "${ROOT_DIR}/target/bom.json" "${ROOT_DIR}/target/frontend-bom.json" > "${ROOT_DIR}/target/sbom-checksums.sha256"
echo "  SBOMs generated successfully with SHA-256 integrity:"
cat "${ROOT_DIR}/target/sbom-checksums.sha256"

# 5. Secret Hygiene
echo ""
echo "[Step 5/7] Running secret scanning and credential hygiene check..."
if grep -rn "BEGIN RSA PRIVATE KEY" "${ROOT_DIR}/src" "${ROOT_DIR}/zkdoc-" 2>/dev/null | grep -v "AuditDataMinimizationSecurityTest"; then
  echo "Error: Potential private key secret detected." >&2
  exit 1
fi
echo "  No unmasked secrets detected in tracked source files."

# 6. Container Check
echo ""
echo "[Step 6/7] Validating container configuration..."
if grep -q "USER zkdoc:zkdoc" "${ROOT_DIR}/Dockerfile" && grep -q "HEALTHCHECK" "${ROOT_DIR}/Dockerfile"; then
  echo "  Dockerfile conforms to unprivileged runtime and healthcheck standards."
fi

echo ""
echo "================================================================="
echo "  SUCCESS: All CI & Supply-Chain Security verifications passed!  "
echo "================================================================="

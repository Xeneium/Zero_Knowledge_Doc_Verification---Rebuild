#!/usr/bin/env bash
# =================================================================
# ZKDoc Platform - Automated Demo Environment Launcher (Bash)
# =================================================================
set -eo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo ""
echo "================================================================="
echo "   ZKDoc Platform - Demonstration Setup & Launch Assistant       "
echo "================================================================="
echo ""

# 1. Check Java 21
echo "[1/4] Checking Java 21 environment..."
if ! command -v java &> /dev/null; then
    echo "[!] Error: Java 21 is not installed or not found on PATH."
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -lt 21 ]; then
    echo "[!] Warning: Detected Java version $JAVA_VER. Java 21 LTS is recommended."
else
    echo "      Java 21 environment validated."
fi

# 2. Check & Generate PDF Fixtures
echo "[2/4] Verifying synthetic test PDF fixtures..."
FIXTURES_DIR="${ROOT_DIR}/demo/fixtures"
if [ ! -f "${FIXTURES_DIR}/demo-degree-diploma.pdf" ] || [ ! -f "${FIXTURES_DIR}/demo-degree-diploma-tampered.pdf" ]; then
    echo "      Generating PDF fixtures via Python..."
    if command -v python3 &> /dev/null; then
        python3 "${FIXTURES_DIR}/generate_pdfs.py"
    elif command -v python &> /dev/null; then
        python "${FIXTURES_DIR}/generate_pdfs.py"
    else
        echo "[!] Python not found to generate fixtures automatically."
    fi
fi
echo "      Synthetic fixtures confirmed in demo/fixtures/"

# 3. Ensure Storage Directories
echo "[3/4] Ensuring storage directories..."
mkdir -p "${ROOT_DIR}/data/artifacts"
mkdir -p "${ROOT_DIR}/data/db"
echo "      Storage initialized at ${ROOT_DIR}/data/artifacts"

# 4. Display Quick-Reference
echo ""
echo "================================================================="
echo "   DEMO ACCOUNTS READY (Seeded automatically on startup)        "
echo "================================================================="
echo "  Role      | Username        | Purpose / Capabilities"
echo "  ----------+-----------------+---------------------------------"
echo "  ADMIN     | admin-operator  | Key registry, audit log chain, metrics"
echo "  ISSUER    | issuer-agent    | Upload doc, sign SHA-256, issue credentials"
echo "  HOLDER    | alice-chen      | Wallet, Bulletproof ZKP range proofs"
echo "  VERIFIER  | verifier-auditor| Mode A/B verification, verify predicates"
echo "================================================================="
echo ""
echo "  Web Application:   http://localhost:8080"
echo "  Quick-Connect:     http://localhost:8080/login"
echo "  Public Verify:     http://localhost:8080/verify"
echo "  API Demo Tokens:   http://localhost:8080/api/public/demo-tokens"
echo "  Health Actuator:   http://localhost:8080/actuator/health"
echo "================================================================="
echo ""

if [ "$1" == "--docker" ]; then
    echo "[*] Launching platform via Docker Compose..."
    docker compose up --build
else
    echo "[*] Launching ZKDoc Platform with Demo Seeding via Maven..."
    "${ROOT_DIR}/mvnw" spring-boot:run -pl zkdoc-api -Dspring-boot.run.arguments="--zkdoc.demo.seed-enabled=true"
fi

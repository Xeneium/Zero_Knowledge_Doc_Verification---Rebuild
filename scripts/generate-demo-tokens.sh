#!/usr/bin/env bash
# =================================================================
# ZKDoc Platform - Demonstration Bearer JWT Token Utility (Bash)
# =================================================================
set -eo pipefail

BASE_URL="${1:-http://localhost:8080}"
ENDPOINT="${BASE_URL}/api/public/demo-tokens"

echo ""
echo "================================================================="
echo "   ZKDoc Platform - Demonstration Bearer JWT Token Utility       "
echo "================================================================="
echo ""

echo "[*] Querying public demo token endpoint: ${ENDPOINT}..."

if ! command -v curl &> /dev/null; then
    echo "[!] Error: curl is required to run this script."
    exit 1
fi

RESPONSE=$(curl -s -f -H "Accept: application/json" "${ENDPOINT}" || true)

if [ -z "$RESPONSE" ]; then
    echo "[!] Could not connect to ZKDoc server at ${ENDPOINT}"
    echo "    Make sure the application is running with demo seed enabled:"
    echo "    ./mvnw spring-boot:run -pl zkdoc-api -Dzkdoc.demo.seed-enabled=true"
    echo "    or launch the demo launcher:"
    echo "    ./scripts/demo-setup.sh"
    echo ""
    exit 1
fi

if command -v jq &> /dev/null; then
    echo "[+] Active Demo Tokens:"
    echo ""
    echo "$RESPONSE" | jq -r '.accounts[] | "-----------------------------------------------------------------\nROLE:        \(.role)\nUSERNAME:    \(.username)\nSUBJECT:     \(.subject)\nDESCRIPTION: \(.description)\nTOKEN:\n\(.token)\n"'
else
    echo "[+] Raw JSON Response (install jq for pretty-printing):"
    echo "$RESPONSE"
fi

echo "================================================================="

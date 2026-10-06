#!/usr/bin/env bash
# End-to-end smoke test against a running instance (docker run or docker compose).
# Usage: scripts/smoke.sh [BASE_URL]      default http://localhost:8080
# Needs: curl, python3, shasum (or sha256sum).
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

json() { python3 -c "import sys, json; print(json.load(sys.stdin)$1)"; }
sha() { if command -v sha256sum >/dev/null; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi; }
step() { printf '\n==> %s\n' "$*"; }

step "Waiting for $BASE_URL to be healthy"
for _ in $(seq 1 60); do
  curl -fs "$BASE_URL/actuator/health" >/dev/null 2>&1 && break
  sleep 1
done
curl -fs "$BASE_URL/actuator/health"; echo

# Unique identities per run so the script can be re-run against a persistent instance without a 409.
RUN_ID="$(date -u +%s)"
CUSTOMER_ID="C-smoke-$RUN_ID"
ACCOUNT="9${RUN_ID: -9}"

step "Minting tokens (dev profile)"
ADMIN=$(curl -fsS -X POST "$BASE_URL/dev/token" -H 'Content-Type: application/json' \
  -d '{"subject":"ops-admin","roles":["ADMIN"]}' | json '["token"]')
CUSTOMER=$(curl -fsS -X POST "$BASE_URL/dev/token" -H 'Content-Type: application/json' \
  -d "{\"subject\":\"$CUSTOMER_ID\",\"roles\":[\"CUSTOMER\"]}" | json '["token"]')

step "Uploading a statement as ops-admin"
printf '%%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] >>\nendobj\ntrailer\n<< /Root 1 0 R >>\n%%%%EOF\n' > "$WORK/statement.pdf"
PERIOD="$(date -u +%Y-%m)"
STATEMENT_ID=$(curl -fsS -X POST "$BASE_URL/api/admin/statements" -H "Authorization: Bearer $ADMIN" \
  -F "file=@$WORK/statement.pdf;type=application/pdf" -F "customerId=$CUSTOMER_ID" -F "accountNumber=$ACCOUNT" -F "period=$PERIOD" \
  | json '["statementId"]')
echo "statementId=$STATEMENT_ID"

step "Listing statements as $CUSTOMER_ID"
LISTED=$(curl -fsS "$BASE_URL/api/statements" -H "Authorization: Bearer $CUSTOMER" | json '[0]["statementId"]')
echo "listed=$LISTED"
[ "$LISTED" = "$STATEMENT_ID" ]

step "Issuing a download link"
ISSUE=$(curl -fsS -X POST "$BASE_URL/api/statements/$STATEMENT_ID/links" -H "Authorization: Bearer $CUSTOMER")
URL=$(echo "$ISSUE" | json '["url"]')
LINK_ID=$(echo "$ISSUE" | json '["linkId"]')
TOKEN="${URL##*/}"
echo "linkId=$LINK_ID"

step "First download: expect 200 and identical bytes"
curl -fsS -o "$WORK/downloaded.pdf" -D "$WORK/headers.txt" "$BASE_URL/download/$TOKEN"
grep -i 'content-disposition' "$WORK/headers.txt"
if [ "$(sha "$WORK/statement.pdf")" = "$(sha "$WORK/downloaded.pdf")" ]; then
  echo "sha256 matches"
else
  echo "sha256 MISMATCH"; exit 1
fi

step "Second download: expect constant 404"
CODE=$(curl -s -o "$WORK/second.json" -w '%{http_code}' "$BASE_URL/download/$TOKEN")
echo "status=$CODE body=$(cat "$WORK/second.json")"
[ "$CODE" = "404" ]

step "Link listing shows EXHAUSTED"
STATUS=$(curl -fsS "$BASE_URL/api/statements/$STATEMENT_ID/links" -H "Authorization: Bearer $CUSTOMER" | json '[0]["status"]')
echo "status=$STATUS"
[ "$STATUS" = "EXHAUSTED" ]

step "Issue another link, revoke it, expect 404 on download"
ISSUE2=$(curl -fsS -X POST "$BASE_URL/api/statements/$STATEMENT_ID/links" -H "Authorization: Bearer $CUSTOMER")
URL2=$(echo "$ISSUE2" | json '["url"]')
LINK2="${URL2##*/}"
LINK2_ID=$(echo "$ISSUE2" | json '["linkId"]')
CODE=$(curl -sS -X DELETE "$BASE_URL/api/links/$LINK2_ID" -H "Authorization: Bearer $CUSTOMER" -o /dev/null -w '%{http_code}')
echo "revoke status=$CODE"
[ "$CODE" = "204" ]
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/download/$LINK2")
echo "download after revoke status=$CODE"
[ "$CODE" = "404" ]

step "HEAD on a fresh link does not consume it"
URL3=$(curl -fsS -X POST "$BASE_URL/api/statements/$STATEMENT_ID/links" -H "Authorization: Bearer $CUSTOMER" | json '["url"]')
LINK3="${URL3##*/}"
CODE=$(curl -sS -I -o /dev/null -w '%{http_code}' "$BASE_URL/download/$LINK3")
echo "HEAD status=$CODE"
[ "$CODE" = "404" ]
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/download/$LINK3")
echo "GET after HEAD status=$CODE"
[ "$CODE" = "200" ]

printf '\nSMOKE TEST PASSED\n'

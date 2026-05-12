#!/usr/bin/env bash
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-grameenlight-a1898}"
BASE_URL="https://firestore.googleapis.com/v1/projects/${PROJECT_ID}/databases/(default)/documents"
TOKEN="$(gcloud auth print-access-token)"

delete_collection() {
  local collection="$1"
  echo "Deleting ${collection}..."
  curl -sS -H "Authorization: Bearer ${TOKEN}" \
    "${BASE_URL}/${collection}?pageSize=300" |
    python3 -c 'import json,sys
data=json.load(sys.stdin)
for doc in data.get("documents", []):
    print(doc["name"])' |
    while IFS= read -r doc_name; do
      if [[ -n "${doc_name}" ]]; then
        curl -sS -X DELETE -H "Authorization: Bearer ${TOKEN}" \
          "https://firestore.googleapis.com/v1/${doc_name}" >/dev/null
        echo "  deleted ${doc_name##*/}"
      fi
    done
}

delete_collection "poleReports"
delete_collection "poleStatuses"

NOW_MILLIS="$(date +%s%3N)"
ENERGY_MONTH_KEY="$(date +%Y-%m)"

for number in $(seq -w 1 12); do
  pole_id="P${number}"
  curl -sS -X PATCH \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    "${BASE_URL}/poleStatuses/${pole_id}" \
    -d "{
      \"fields\": {
        \"poleId\": {\"stringValue\": \"${pole_id}\"},
        \"status\": {\"stringValue\": \"WORKING\"},
        \"repairState\": {\"stringValue\": \"FIXED\"},
        \"updatedAtMillis\": {\"integerValue\": \"${NOW_MILLIS}\"},
        \"daytimeWasteMillis\": {\"integerValue\": \"0\"},
        \"energyMonthKey\": {\"stringValue\": \"${ENERGY_MONTH_KEY}\"}
      }
    }" >/dev/null
  echo "  created green status for ${pole_id}"
done

echo "Done. Firestore now has no reports and 12 green pole status docs."

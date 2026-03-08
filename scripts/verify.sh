#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log() {
  printf '[verify-maven] %s\n' "$1"
}

check_code() {
  local name="$1"
  local url="$2"
  local expected="$3"
  local code
  local retries=30
  local i
  for ((i=1; i<=retries; i++)); do
    code=$(curl -s -o "/tmp/${name}.json" -w "%{http_code}" "$url" || true)
    if [[ "$code" == "$expected" ]]; then
      printf '%-24s %s\n' "$name" "$code"
      return 0
    fi
    sleep 2
  done
  printf '%-24s %s\n' "$name" "$code"
  echo "ERROR: ${name} expected ${expected}, got ${code}" >&2
  return 1
}

check_post() {
  local name="$1"
  local url="$2"
  local payload="$3"
  local expected="$4"
  local code
  code=$(curl -s -H 'Content-Type: application/json' \
    -o "/tmp/${name}.json" \
    -w "%{http_code}" \
    -X POST "$url" \
    -d "$payload" || true)
  printf '%-24s %s\n' "$name" "$code"
  if [[ "$code" != "$expected" ]]; then
    echo "ERROR: ${name} expected ${expected}, got ${code}" >&2
    return 1
  fi
}

check_post_with_participant() {
  local name="$1"
  local url="$2"
  local participant="$3"
  local payload="$4"
  local expected="$5"
  local code
  code=$(curl -s -H 'Content-Type: application/json' \
    -H "X-Participant-Id: ${participant}" \
    -o "/tmp/${name}.json" \
    -w "%{http_code}" \
    -X POST "$url" \
    -d "$payload" || true)
  printf '%-24s %s\n' "$name" "$code"
  if [[ "$code" != "$expected" ]]; then
    echo "ERROR: ${name} expected ${expected}, got ${code}" >&2
    return 1
  fi
}

query_count() {
  local sql="$1"
  docker compose exec -T mysql env MYSQL_PWD=edc mysql -u edc -D edc -Nse "$sql" | tr -d '\r'
}

wait_dataplanes_ready() {
  local retries=40
  local i
  local code
  local count=0
  for ((i=1; i<=retries; i++)); do
    code=$(curl -s -o /tmp/dataplanes-ready.json -w "%{http_code}" "http://localhost:8181/api/dataplanes" || true)
    if [[ "$code" == "200" ]]; then
      count=$(python3 - <<'PY'
import json
with open("/tmp/dataplanes-ready.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(len(data) if isinstance(data, list) else 0)
PY
)
      if [[ "$count" -ge 2 ]]; then
        printf '%-24s %s\n' "dataplanes-ready" "$count"
        return 0
      fi
    fi
    sleep 2
  done
  printf '%-24s %s\n' "dataplanes-ready" "$count"
  echo "ERROR: dataplanes-ready expected >= 2, got ${count}" >&2
  return 1
}

assert_min() {
  local name="$1"
  local value="$2"
  local expected="$3"
  if [[ "$value" -lt "$expected" ]]; then
    echo "ERROR: ${name} expected >= ${expected}, got ${value}" >&2
    exit 1
  fi
}

log "启动 Maven/SpringBoot 全栈（含双 Data Plane）"
docker compose up -d --build >/dev/null

log "服务健康检查"
for port in 8181 8182 8183 8184 8185 8186 8187; do
  check_code "health-${port}" "http://localhost:${port}/actuator/health" "200"
done
check_code "frontend-index" "http://localhost:8080" "200"
if ! grep -q "EDC 商用数据空间控制台" /tmp/frontend-index.json; then
  echo "ERROR: frontend title not found" >&2
  exit 1
fi
wait_dataplanes_ready

log "运行控制面完整流程（生成资产/协商/传输）"
check_post_with_participant "qual-issuance" "http://localhost:8184/api/issuer/credentials" "participant-b" '{"type":"CommercialAccessCredential","issuer":"issuer-A","claims":{"participant":"participant-b","scope":"CATALOG_ACCESS"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
issuance_id=$(python3 - <<'PY'
import json
with open("/tmp/qual-issuance.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data.get("issuanceId", ""))
PY
)
if [[ -z "$issuance_id" ]]; then
  echo "ERROR: qualification issuance id missing" >&2
  exit 1
fi
check_post_with_participant "qual-cred" "http://localhost:8183/api/identity/credentials" "participant-b" "{\"type\":\"MembershipCredential\",\"issuer\":\"issuer-A\",\"claims\":{\"participant\":\"participant-b\",\"scope\":\"CATALOG_ACCESS\"},\"expiresAt\":\"2026-12-31T00:00:00Z\",\"issuanceId\":\"${issuance_id}\"}" "200"
credential_id=$(python3 - <<'PY'
import json
with open("/tmp/qual-cred.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data.get("id", ""))
PY
)
if [[ -z "$credential_id" ]]; then
  echo "ERROR: qualification credential id missing" >&2
  exit 1
fi
check_post_with_participant "qual-presentation" "http://localhost:8183/api/dcp/presentations" "participant-b" "{\"credentialId\":\"${credential_id}\",\"audience\":\"participant-a\"}" "200"
presentation_id=$(python3 - <<'PY'
import json
with open("/tmp/qual-presentation.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data.get("presentationId", ""))
PY
)
if [[ -z "$presentation_id" ]]; then
  echo "ERROR: qualification presentation id missing" >&2
  exit 1
fi
check_post_with_participant "qual-verify" "http://localhost:8183/api/dcp/verification" "participant-a" "{\"presentationId\":\"${presentation_id}\"}" "200"
check_code "qual-status" "http://localhost:8183/api/dcp/qualification?participantId=participant-b" "200"
qualification_ok=$(python3 - <<'PY'
import json
with open("/tmp/qual-status.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(str(bool(data.get("qualified"))).lower())
PY
)
if [[ "$qualification_ok" != "true" ]]; then
  echo "ERROR: participant-b qualification expected true, got ${qualification_ok}" >&2
  exit 1
fi

check_post "scenario-run" "http://localhost:8181/api/scenario/run" '{"assetCount":3,"consumerId":"participant-b"}' "200"

check_code "agreements-list" "http://localhost:8181/api/contracts/agreements" "200"
agreement_id=$(python3 - <<'PY'
import json
with open("/tmp/agreements-list.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data[0].get("agreementId", "") if isinstance(data, list) and data else "")
PY
)
if [[ -z "$agreement_id" ]]; then
  echo "ERROR: agreement list has no agreementId" >&2
  exit 1
fi

orchestration_forbidden_code=$(curl -s -o /tmp/orchestration-preview-forbidden.json -w "%{http_code}" \
  "http://localhost:8181/api/transfers/orchestration/preview?agreementId=${agreement_id}" || true)
printf '%-24s %s\n' "orchestration-403" "$orchestration_forbidden_code"
if [[ "$orchestration_forbidden_code" != "403" ]]; then
  echo "ERROR: orchestration preview without permission expected 403, got ${orchestration_forbidden_code}" >&2
  exit 1
fi

orchestration_allowed_code=$(curl -s \
  -H "X-Participant-Id: operator" \
  -H "X-Operator-Token: operator-demo-key" \
  -o /tmp/orchestration-preview-200.json \
  -w "%{http_code}" \
  "http://localhost:8181/api/transfers/orchestration/preview?agreementId=${agreement_id}" || true)
printf '%-24s %s\n' "orchestration-200" "$orchestration_allowed_code"
if [[ "$orchestration_allowed_code" != "200" ]]; then
  echo "ERROR: orchestration preview with permission expected 200, got ${orchestration_allowed_code}" >&2
  exit 1
fi
orchestration_steps=$(python3 - <<'PY'
import json
with open("/tmp/orchestration-preview-200.json", "r", encoding="utf-8") as f:
    data = json.load(f)
steps = data.get("steps")
print(len(steps) if isinstance(steps, list) else 0)
PY
)
assert_min "orchestration steps" "$orchestration_steps" 4

transfer_id=$(python3 - <<'PY'
import json
with open("/tmp/scenario-run.json", "r", encoding="utf-8") as f:
    data = json.load(f)
ids = data.get("transferIds") or []
print(ids[0] if ids else "")
PY
)
if [[ -z "$transfer_id" ]]; then
  echo "ERROR: scenario response has no transferIds" >&2
  exit 1
fi

check_code "transfer-edr" "http://localhost:8181/api/transfers/${transfer_id}/edr" "200"
edr_endpoint=$(python3 - <<'PY'
import json
with open("/tmp/transfer-edr.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data.get("endpoint", ""))
PY
)
edr_token=$(python3 - <<'PY'
import json
with open("/tmp/transfer-edr.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(data.get("authToken", ""))
PY
)
if [[ -z "$edr_endpoint" || -z "$edr_token" ]]; then
  echo "ERROR: invalid EDR response" >&2
  exit 1
fi
pull_code=$(curl -s -H "Authorization: ${edr_token}" -o /tmp/transfer-pull.json -w "%{http_code}" "${edr_endpoint}?message=verify")
printf '%-24s %s\n' "transfer-pull" "$pull_code"
if [[ "$pull_code" != "200" ]]; then
  echo "ERROR: transfer pull failed" >&2
  exit 1
fi

log "验证双 Data Plane 注册与持久化"
check_code "dataplanes-list" "http://localhost:8181/api/dataplanes" "200"
dataplane_size=$(python3 - <<'PY'
import json
with open("/tmp/dataplanes-list.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(len(data) if isinstance(data, list) else 0)
PY
)
assert_min "registered dataplanes" "$dataplane_size" 2

cp_asset_count="$(query_count 'select count(*) from edc_cp_asset;')"
cp_neg_count="$(query_count 'select count(*) from edc_cp_contract_negotiation;')"
cp_transfer_count="$(query_count 'select count(*) from edc_cp_transfer_process;')"
cp_dp_count="$(query_count 'select count(*) from edc_cp_data_plane_instance;')"
cp_transfer_dp_distinct="$(query_count 'select count(distinct data_plane_id) from edc_cp_transfer_process;')"
dp_transfer_count="$(query_count 'select count(*) from edc_dp_transfer_process;')"
dp_transfer_dp_distinct="$(query_count 'select count(distinct data_plane_id) from edc_dp_transfer_process;')"

assert_min "edc_cp_asset" "$cp_asset_count" 3
assert_min "edc_cp_contract_negotiation" "$cp_neg_count" 3
assert_min "edc_cp_transfer_process" "$cp_transfer_count" 3
assert_min "edc_cp_data_plane_instance" "$cp_dp_count" 2
assert_min "edc_cp_transfer_process distinct data_plane_id" "$cp_transfer_dp_distinct" 2
assert_min "edc_dp_transfer_process" "$dp_transfer_count" 3
assert_min "edc_dp_transfer_process distinct data_plane_id" "$dp_transfer_dp_distinct" 2

log "补充验证其余后端模块接口"
check_code "catalog" "http://localhost:8181/api/catalog" "200"
check_code "dp1-info" "http://localhost:8182/api/dataplane/info" "200"
check_code "dp2-info" "http://localhost:8187/api/dataplane/info" "200"
check_code "id-did" "http://localhost:8183/api/identity/did" "200"
check_post "id-cred" "http://localhost:8183/api/identity/credentials" '{"type":"MembershipCredential","issuer":"issuer-A","claims":{"level":"GOLD"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
check_post "issuer-cred" "http://localhost:8184/api/issuer/credentials" '{"type":"MembershipCredential","issuer":"issuer-A","claims":{"level":"GOLD"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
check_code "fed-catalog" "http://localhost:8185/api/federated/catalog" "200"
check_post "op-member" "http://localhost:8186/api/memberships" '{"participantId":"participant-a","level":"GOLD","validTo":"2026-12-31T00:00:00Z"}' "200"
check_post "billing-check" "http://localhost:8186/api/billing/usage/check" '{"participantId":"participant-a","serviceCode":"ISSUER_CREDENTIAL_ISSUE"}' "200"
billing_allowed=$(python3 - <<'PY'
import json
with open("/tmp/billing-check.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(str(bool(data.get("allowed"))).lower())
PY
)
if [[ "$billing_allowed" != "true" ]]; then
  echo "ERROR: billing-check allowed expected true, got ${billing_allowed}" >&2
  exit 1
fi

log "持久化统计"
printf '  edc_cp_asset                                  = %s\n' "$cp_asset_count"
printf '  edc_cp_contract_negotiation                   = %s\n' "$cp_neg_count"
printf '  edc_cp_transfer_process                       = %s\n' "$cp_transfer_count"
printf '  edc_cp_data_plane_instance                    = %s\n' "$cp_dp_count"
printf '  edc_cp_transfer_process distinct data_plane   = %s\n' "$cp_transfer_dp_distinct"
printf '  edc_dp_transfer_process                       = %s\n' "$dp_transfer_count"
printf '  edc_dp_transfer_process distinct data_plane   = %s\n' "$dp_transfer_dp_distinct"

log "Maven/JDK21 商业流程验证通过"

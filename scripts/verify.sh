#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GATEWAY_BASE="${GATEWAY_BASE:-${EDC_PUBLIC_BASE_URL:-http://localhost:18080}}"
CONTROL_BASE="${CONTROL_BASE:-${GATEWAY_BASE}/cp}"
IDENTITY_BASE="${IDENTITY_BASE:-${GATEWAY_BASE}/ih}"
ISSUER_BASE="${ISSUER_BASE:-${GATEWAY_BASE}/is}"
FEDERATED_BASE="${FEDERATED_BASE:-${GATEWAY_BASE}/fc}"
OPERATOR_BASE="${OPERATOR_BASE:-${GATEWAY_BASE}/op}"
DP1_BASE="${DP1_BASE:-${GATEWAY_BASE}/dp1}"
DP2_BASE="${DP2_BASE:-${GATEWAY_BASE}/dp2}"

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
    code=$(curl -s -o /tmp/dataplanes-ready.json -w "%{http_code}" "${CONTROL_BASE}/api/dataplanes" || true)
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
check_code "frontend-index" "${GATEWAY_BASE}" "200"
if ! grep -q "EDC 商用数据空间控制台" /tmp/frontend-index.json; then
  echo "ERROR: frontend title not found" >&2
  exit 1
fi
check_code "gateway-cp" "${CONTROL_BASE}/actuator/health" "200"
check_code "gateway-ih" "${IDENTITY_BASE}/actuator/health" "200"
check_code "gateway-is" "${ISSUER_BASE}/actuator/health" "200"
check_code "gateway-fc" "${FEDERATED_BASE}/actuator/health" "200"
check_code "gateway-op" "${OPERATOR_BASE}/actuator/health" "200"
check_code "gateway-dp1" "${DP1_BASE}/actuator/health" "200"
check_code "gateway-dp2" "${DP2_BASE}/actuator/health" "200"
wait_dataplanes_ready

log "运行控制面完整流程（生成资产/协商/传输）"
check_post_with_participant "qual-issuance" "${ISSUER_BASE}/api/issuer/credentials" "participant-b" '{"type":"CommercialAccessCredential","issuer":"issuer-A","claims":{"participant":"participant-b","scope":"CATALOG_ACCESS"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
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
check_post_with_participant "qual-cred" "${IDENTITY_BASE}/api/identity/credentials" "participant-b" "{\"type\":\"MembershipCredential\",\"issuer\":\"issuer-A\",\"claims\":{\"participant\":\"participant-b\",\"scope\":\"CATALOG_ACCESS\"},\"expiresAt\":\"2026-12-31T00:00:00Z\",\"issuanceId\":\"${issuance_id}\"}" "200"
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
check_post_with_participant "qual-presentation" "${IDENTITY_BASE}/api/dcp/presentations" "participant-b" "{\"credentialId\":\"${credential_id}\",\"audience\":\"participant-a\"}" "200"
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
check_post_with_participant "qual-verify" "${IDENTITY_BASE}/api/dcp/verification" "participant-a" "{\"presentationId\":\"${presentation_id}\"}" "200"
check_code "qual-status" "${IDENTITY_BASE}/api/dcp/qualification?participantId=participant-b" "200"
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

check_post "scenario-run" "${CONTROL_BASE}/api/scenario/run" '{"assetCount":3,"consumerId":"participant-b"}' "200"

check_code "agreements-list" "${CONTROL_BASE}/api/contracts/agreements" "200"
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
  "${CONTROL_BASE}/api/transfers/orchestration/preview?agreementId=${agreement_id}" || true)
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
  "${CONTROL_BASE}/api/transfers/orchestration/preview?agreementId=${agreement_id}" || true)
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

check_code "transfer-edr" "${CONTROL_BASE}/api/transfers/${transfer_id}/edr" "200"
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
case "$edr_endpoint" in
  "$GATEWAY_BASE"/dp1/*|"$GATEWAY_BASE"/dp2/*) ;;
  *)
    echo "ERROR: EDR endpoint expected to be exposed by gateway ${GATEWAY_BASE}/dp1|dp2, got ${edr_endpoint}" >&2
    exit 1
    ;;
esac
pull_code=$(curl -s -H "Authorization: ${edr_token}" -o /tmp/transfer-pull.json -w "%{http_code}" "${edr_endpoint}?message=verify")
printf '%-24s %s\n' "transfer-pull" "$pull_code"
if [[ "$pull_code" != "200" ]]; then
  echo "ERROR: transfer pull failed" >&2
  exit 1
fi

log "验证双 Data Plane 注册与持久化"
check_code "dataplanes-list" "${CONTROL_BASE}/api/dataplanes" "200"
dataplane_size=$(python3 - <<'PY'
import json
with open("/tmp/dataplanes-list.json", "r", encoding="utf-8") as f:
    data = json.load(f)
print(len(data) if isinstance(data, list) else 0)
PY
)
assert_min "registered dataplanes" "$dataplane_size" 2

cp_asset_count="$(query_count 'select count(*) from edc_cp_asset;')"
cp_offer_count="$(query_count 'select count(*) from edc_cp_contract_offer;')"
cp_neg_count="$(query_count 'select count(*) from edc_cp_contract_negotiation;')"
cp_transfer_count="$(query_count 'select count(*) from edc_cp_transfer_process;')"
cp_dp_count="$(query_count 'select count(*) from edc_cp_data_plane_instance;')"
cp_transfer_dp_distinct="$(query_count 'select count(distinct data_plane_id) from edc_cp_transfer_process;')"
dp_transfer_count="$(query_count 'select count(*) from edc_dp_transfer_process;')"
dp_transfer_dp_distinct="$(query_count 'select count(distinct data_plane_id) from edc_dp_transfer_process;')"
fc_offer_match_count="$(query_count 'select count(*) from edc_fc_catalog_item f join edc_cp_contract_offer o on f.offer_id = o.id;')"
audit_event_count="$(query_count 'select count(*) from edc_op_audit_event;')"
billing_record_count="$(query_count 'select count(*) from edc_op_billing_record;')"

assert_min "edc_cp_asset" "$cp_asset_count" 3
assert_min "edc_cp_contract_offer" "$cp_offer_count" 3
assert_min "edc_cp_contract_negotiation" "$cp_neg_count" 3
assert_min "edc_cp_transfer_process" "$cp_transfer_count" 3
assert_min "edc_cp_data_plane_instance" "$cp_dp_count" 2
assert_min "edc_cp_transfer_process distinct data_plane_id" "$cp_transfer_dp_distinct" 2
assert_min "edc_dp_transfer_process" "$dp_transfer_count" 3
assert_min "edc_dp_transfer_process distinct data_plane_id" "$dp_transfer_dp_distinct" 2
assert_min "edc_fc_catalog_item mirrored offers" "$fc_offer_match_count" "$cp_offer_count"
assert_min "edc_op_audit_event" "$audit_event_count" 1
assert_min "edc_op_billing_record" "$billing_record_count" 1

log "补充验证其余后端模块接口"
check_code "catalog" "${CONTROL_BASE}/api/catalog" "200"
check_code "dp1-info" "${DP1_BASE}/api/dataplane/info" "200"
check_code "dp2-info" "${DP2_BASE}/api/dataplane/info" "200"
check_code "id-did" "${IDENTITY_BASE}/api/identity/did" "200"
check_post "id-cred" "${IDENTITY_BASE}/api/identity/credentials" '{"type":"MembershipCredential","issuer":"issuer-A","claims":{"level":"GOLD"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
check_post "issuer-cred" "${ISSUER_BASE}/api/issuer/credentials" '{"type":"MembershipCredential","issuer":"issuer-A","claims":{"level":"GOLD"},"expiresAt":"2026-12-31T00:00:00Z"}' "200"
check_code "fed-catalog" "${FEDERATED_BASE}/api/federated/catalog" "200"
check_post "op-member" "${OPERATOR_BASE}/api/memberships" '{"participantId":"participant-a","level":"GOLD","validTo":"2026-12-31T00:00:00Z"}' "200"
check_post "billing-check" "${OPERATOR_BASE}/api/billing/usage/check" '{"participantId":"participant-a","serviceCode":"ISSUER_CREDENTIAL_ISSUE"}' "200"
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
printf '  edc_cp_contract_offer                         = %s\n' "$cp_offer_count"
printf '  edc_cp_contract_negotiation                   = %s\n' "$cp_neg_count"
printf '  edc_cp_transfer_process                       = %s\n' "$cp_transfer_count"
printf '  edc_cp_data_plane_instance                    = %s\n' "$cp_dp_count"
printf '  edc_cp_transfer_process distinct data_plane   = %s\n' "$cp_transfer_dp_distinct"
printf '  edc_dp_transfer_process                       = %s\n' "$dp_transfer_count"
printf '  edc_dp_transfer_process distinct data_plane   = %s\n' "$dp_transfer_dp_distinct"
printf '  edc_fc_catalog_item mirrored offers           = %s\n' "$fc_offer_match_count"
printf '  edc_op_audit_event                            = %s\n' "$audit_event_count"
printf '  edc_op_billing_record                         = %s\n' "$billing_record_count"

log "Maven/JDK21 商业流程验证通过"

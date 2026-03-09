<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel-head">
        <h3>治理模块接口巡检（由 Control Plane 汇总）</h3>
        <button class="btn" type="button" @click="runQuickCheck" :disabled="quickChecking">
          {{ quickChecking ? '巡检中...' : '一键巡检' }}
        </button>
      </div>

      <table class="table">
        <thead>
          <tr>
            <th>模块</th>
            <th>检查项</th>
            <th>方法</th>
            <th>状态</th>
            <th>时间</th>
            <th>说明</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in pagedMonitorStatuses" :key="item.moduleName + item.checkName + item.method">
            <td>{{ item.moduleName }}</td>
            <td>{{ item.checkName }}</td>
            <td class="mono">{{ item.method }}</td>
            <td>
              <span class="state-chip" :class="statusClass(item.status)">{{ item.statusCode ?? '-' }}</span>
            </td>
            <td>{{ formatDateTime(item.checkedAt) }}</td>
            <td class="mono">{{ item.message }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="monitorStatuses.length" class="pager">
        <button class="btn" type="button" @click="monitorPage -= 1" :disabled="monitorPage <= 1">上一页</button>
        <p class="muted">第 {{ monitorPage }} / {{ monitorPageCount }} 页 · 共 {{ sortedMonitorStatuses.length }} 条</p>
        <button class="btn" type="button" @click="monitorPage += 1" :disabled="monitorPage >= monitorPageCount">下一页</button>
      </div>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="panel-head">
          <h3>Identity Hub（运营方）</h3>
          <p class="muted">认证人、凭证、DCP 展示</p>
        </div>

        <label>
          参与方
          <input v-model.trim="participantId" type="text" />
        </label>

        <div class="inline-actions wrap">
          <button class="btn" type="button" @click="loadIdentityDid" :disabled="states.identityDid.loading">查询认证人</button>
          <button class="btn" type="button" @click="createIdentityCredential" :disabled="states.identityCredential.loading">写入凭证</button>
          <button class="btn" type="button" @click="createDcpPresentation" :disabled="states.dcpPresentation.loading">创建DCP展示</button>
          <button class="btn" type="button" @click="verifyDcpPresentation" :disabled="states.dcpVerification.loading">校验DCP展示</button>
        </div>

        <div class="hint-box">
          <p>credentialId: <span class="mono">{{ credentialId || '-' }}</span></p>
          <p>presentationId: <span class="mono">{{ presentationId || '-' }}</span></p>
        </div>

        <pre class="json-view">{{ identityOutput }}</pre>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>Issuer Service（运营方）</h3>
          <p class="muted">凭证签发接口</p>
        </div>

        <button class="btn" type="button" @click="issueCredential" :disabled="states.issuerCredential.loading">执行签发</button>
        <div class="hint-box">
          <p>issuerIssuanceId: <span class="mono">{{ issuerIssuanceId || '-' }}</span></p>
        </div>
        <pre class="json-view">{{ states.issuerCredential.output }}</pre>
      </article>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="panel-head">
          <h3>Federated Catalog（消费方）</h3>
          <p class="muted">目录聚合与爬取</p>
        </div>

        <div class="inline-actions wrap">
          <button class="btn" type="button" @click="loadFederatedCatalog" :disabled="states.federatedCatalog.loading">查询目录</button>
          <button class="btn" type="button" @click="triggerFederatedCrawl" :disabled="states.federatedCrawl.loading">触发爬取</button>
        </div>

        <pre class="json-view">{{ federatedOutput }}</pre>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>Operator Services（运营方）</h3>
          <p class="muted">会员与按次计费校验</p>
        </div>

        <div class="inline-actions wrap">
          <button class="btn" type="button" @click="createMembership" :disabled="states.operatorMembershipCreate.loading">创建会员</button>
          <button class="btn" type="button" @click="loadMemberships" :disabled="states.operatorMembershipList.loading">会员列表</button>
          <button class="btn" type="button" @click="checkBillingUsage" :disabled="states.operatorUsage.loading">额度校验</button>
          <button class="btn" type="button" @click="createAuditEvent" :disabled="states.operatorAuditCreate.loading">写入审计事件</button>
          <button class="btn" type="button" @click="loadAuditEvents" :disabled="states.operatorAuditList.loading">审计列表</button>
          <button class="btn" type="button" @click="loadBillingRecords" :disabled="states.operatorBillingList.loading">账单列表</button>
        </div>

        <label>
          服务编码
          <input v-model.trim="serviceCode" type="text" />
        </label>

        <pre class="json-view">{{ operatorOutput }}</pre>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  CONTROL_BASE,
  FEDERATED_BASE,
  IDENTITY_BASE,
  ISSUER_BASE,
  OPERATOR_BASE,
  prettyJson,
  requestJson
} from '../lib/http'

type ApiState = {
  loading: boolean
  output: string
  status: string
}

type MonitorStatus = {
  moduleName: string
  checkName: string
  method: string
  endpoint: string
  statusCode: number | null
  status: string
  message: string
  checkedAt: string
}

type EndpointKey =
  | 'identityDid'
  | 'identityCredential'
  | 'dcpPresentation'
  | 'dcpVerification'
  | 'issuerCredential'
  | 'federatedCatalog'
  | 'federatedCrawl'
  | 'operatorMembershipCreate'
  | 'operatorMembershipList'
  | 'operatorAuditCreate'
  | 'operatorAuditList'
  | 'operatorBillingList'
  | 'operatorUsage'

const participantId = ref('participant-a')
const credentialId = ref('')
const presentationId = ref('')
const issuerIssuanceId = ref('')
const serviceCode = ref('IDENTITY_CREDENTIAL_WRITE')
const quickChecking = ref(false)
const monitorStatuses = ref<MonitorStatus[]>([])
const PAGE_SIZE = 10
const monitorPage = ref(1)

function toTimeMillis(value: string): number {
  if (!value) {
    return 0
  }
  const parsed = Date.parse(value.replace(' ', 'T'))
  return Number.isNaN(parsed) ? 0 : parsed
}

function formatDateTime(value: string): string {
  const ms = toTimeMillis(value)
  if (ms <= 0) {
    return '-'
  }
  return new Date(ms).toLocaleString()
}

function paginate<T>(items: T[], page: number): T[] {
  const start = (page - 1) * PAGE_SIZE
  return items.slice(start, start + PAGE_SIZE)
}

const sortedMonitorStatuses = computed(() => {
  return [...monitorStatuses.value].sort((a, b) => toTimeMillis(b.checkedAt) - toTimeMillis(a.checkedAt))
})

const monitorPageCount = computed(() => Math.max(1, Math.ceil(sortedMonitorStatuses.value.length / PAGE_SIZE)))
const pagedMonitorStatuses = computed(() => paginate(sortedMonitorStatuses.value, monitorPage.value))

const states = reactive<Record<EndpointKey, ApiState>>({
  identityDid: { loading: false, output: '未执行', status: 'PENDING' },
  identityCredential: { loading: false, output: '未执行', status: 'PENDING' },
  dcpPresentation: { loading: false, output: '未执行', status: 'PENDING' },
  dcpVerification: { loading: false, output: '未执行', status: 'PENDING' },
  issuerCredential: { loading: false, output: '未执行', status: 'PENDING' },
  federatedCatalog: { loading: false, output: '未执行', status: 'PENDING' },
  federatedCrawl: { loading: false, output: '未执行', status: 'PENDING' },
  operatorMembershipCreate: { loading: false, output: '未执行', status: 'PENDING' },
  operatorMembershipList: { loading: false, output: '未执行', status: 'PENDING' },
  operatorAuditCreate: { loading: false, output: '未执行', status: 'PENDING' },
  operatorAuditList: { loading: false, output: '未执行', status: 'PENDING' },
  operatorBillingList: { loading: false, output: '未执行', status: 'PENDING' },
  operatorUsage: { loading: false, output: '未执行', status: 'PENDING' }
})

const identityOutput = computed(() => {
  return [
    `did => ${states.identityDid.output}`,
    `credential => ${states.identityCredential.output}`,
    `dcpPresentation => ${states.dcpPresentation.output}`,
    `dcpVerification => ${states.dcpVerification.output}`
  ].join('\n\n')
})

const federatedOutput = computed(() => {
  return [`catalog => ${states.federatedCatalog.output}`, `crawl => ${states.federatedCrawl.output}`].join('\n\n')
})

const operatorOutput = computed(() => {
  return [
    `createMembership => ${states.operatorMembershipCreate.output}`,
    `listMembership => ${states.operatorMembershipList.output}`,
    `createAudit => ${states.operatorAuditCreate.output}`,
    `listAudit => ${states.operatorAuditList.output}`,
    `listBilling => ${states.operatorBillingList.output}`,
    `usageCheck => ${states.operatorUsage.output}`
  ].join('\n\n')
})

function normalizeError(err: unknown): string {
  return err instanceof Error ? err.message : '请求失败'
}

function statusClass(status: string): string {
  if (status.includes('DOWN') || status.includes('ERR') || status.includes('ERROR')) {
    return 'err'
  }
  if (status.includes('UP') || status.includes('200') || status.includes('OK')) {
    return 'ok'
  }
  return 'pending'
}

async function runEndpoint(key: EndpointKey, action: () => Promise<unknown>) {
  states[key].loading = true
  try {
    const data = await action()
    states[key].status = '200'
    states[key].output = prettyJson(data)
  } catch (err) {
    states[key].status = 'ERR'
    states[key].output = normalizeError(err)
  } finally {
    states[key].loading = false
  }
}

async function loadGovernanceMonitor() {
  monitorStatuses.value = await requestJson<MonitorStatus[]>(`${CONTROL_BASE}/api/monitor/governance`)
  monitorPage.value = 1
}

async function loadIdentityDid() {
  await runEndpoint('identityDid', () => requestJson<unknown>(`${IDENTITY_BASE}/api/identity/did`))
}

async function createIdentityCredential() {
  await runEndpoint('identityCredential', async () => {
    const data = await requestJson<Record<string, unknown>>(`${IDENTITY_BASE}/api/identity/credentials`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': participantId.value
      },
      body: JSON.stringify({
        type: 'MembershipCredential',
        issuer: 'issuer-A',
        claims: { level: 'GOLD', region: 'CN' },
        expiresAt: '2026-12-31T00:00:00Z',
        issuanceId: issuerIssuanceId.value || undefined
      })
    })

    credentialId.value = typeof data.id === 'string' ? data.id : ''
    return data
  })
}

async function createDcpPresentation() {
  if (!credentialId.value) {
    states.dcpPresentation.status = 'ERR'
    states.dcpPresentation.output = '请先写入凭证。'
    return
  }

  await runEndpoint('dcpPresentation', async () => {
    const data = await requestJson<Record<string, unknown>>(`${IDENTITY_BASE}/api/dcp/presentations`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': participantId.value
      },
      body: JSON.stringify({
        credentialId: credentialId.value,
        audience: 'participant-b'
      })
    })

    presentationId.value = typeof data.presentationId === 'string' ? data.presentationId : ''
    return data
  })
}

async function verifyDcpPresentation() {
  if (!presentationId.value) {
    states.dcpVerification.status = 'ERR'
    states.dcpVerification.output = '请先创建 DCP 展示。'
    return
  }

  await runEndpoint('dcpVerification', () =>
    requestJson<unknown>(`${IDENTITY_BASE}/api/dcp/verification`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': participantId.value
      },
      body: JSON.stringify({ presentationId: presentationId.value })
    })
  )
}

async function issueCredential() {
  await runEndpoint('issuerCredential', async () => {
    const data = await requestJson<Record<string, unknown>>(`${ISSUER_BASE}/api/issuer/credentials`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Participant-Id': participantId.value
      },
      body: JSON.stringify({
        type: 'CommercialAccessCredential',
        issuer: 'issuer-A',
        claims: { participant: participantId.value, tier: 'PLATINUM' },
        expiresAt: '2026-12-31T00:00:00Z'
      })
    })
    issuerIssuanceId.value = typeof data.issuanceId === 'string' ? data.issuanceId : ''
    return data
  })
}

async function loadFederatedCatalog() {
  await runEndpoint('federatedCatalog', () =>
    requestJson<unknown>(`${FEDERATED_BASE}/api/federated/catalog`, {
      headers: { 'X-Participant-Id': participantId.value }
    })
  )
}

async function triggerFederatedCrawl() {
  await runEndpoint('federatedCrawl', () =>
    requestJson<unknown>(`${FEDERATED_BASE}/api/federated/crawl`, {
      method: 'POST',
      headers: { 'X-Participant-Id': participantId.value }
    })
  )
}

async function createMembership() {
  await runEndpoint('operatorMembershipCreate', () =>
    requestJson<unknown>(`${OPERATOR_BASE}/api/memberships`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        participantId: participantId.value,
        level: 'GOLD',
        validTo: '2026-12-31T00:00:00Z'
      })
    })
  )
}

async function loadMemberships() {
  await runEndpoint('operatorMembershipList', () => requestJson<unknown>(`${OPERATOR_BASE}/api/memberships`))
}

async function createAuditEvent() {
  await runEndpoint('operatorAuditCreate', () =>
    requestJson<unknown>(`${OPERATOR_BASE}/api/audit/events`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        eventType: 'OPERATOR_MANUAL_CHECK',
        actorId: participantId.value,
        payload: {
          serviceCode: serviceCode.value,
          action: 'governance-hub-manual-audit'
        },
        signature: 'operator-console'
      })
    })
  )
}

async function loadAuditEvents() {
  await runEndpoint('operatorAuditList', () => requestJson<unknown>(`${OPERATOR_BASE}/api/audit/events`))
}

async function loadBillingRecords() {
  await runEndpoint('operatorBillingList', () => requestJson<unknown>(`${OPERATOR_BASE}/api/billing/records`))
}

async function checkBillingUsage() {
  await runEndpoint('operatorUsage', () =>
    requestJson<unknown>(`${OPERATOR_BASE}/api/billing/usage/check`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        participantId: participantId.value,
        serviceCode: serviceCode.value
      })
    })
  )
}

async function runQuickCheck() {
  quickChecking.value = true
  try {
    await loadGovernanceMonitor()
  } finally {
    quickChecking.value = false
  }
}

onMounted(() => {
  void runQuickCheck()
})
</script>

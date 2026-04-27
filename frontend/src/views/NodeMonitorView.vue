<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel-head">
        <h3>服务健康与端口（由 Control Plane 汇总）</h3>
        <button class="btn" type="button" @click="refreshHealth" :disabled="healthLoading">
          {{ healthLoading ? '检测中...' : '刷新健康状态' }}
        </button>
      </div>

      <div class="ops-summary-grid" aria-label="节点健康摘要">
        <article class="ops-summary-card">
          <p class="stat-label">健康检查</p>
          <p class="stat-value">{{ healthSummary.up }} / {{ healthSummary.total }}</p>
          <p class="stat-note">最近检测：{{ formatDateTime(healthSummary.lastCheckedAt) }}</p>
        </article>
        <article class="ops-summary-card" :class="{ alert: healthSummary.down > 0 }">
          <p class="stat-label">异常项</p>
          <p class="stat-value">{{ healthSummary.down }}</p>
          <p class="stat-note">{{ healthSummary.down > 0 ? '需要优先处理' : '全部服务可用' }}</p>
        </article>
        <article class="ops-summary-card">
          <p class="stat-label">注册 Data Plane</p>
          <p class="stat-value">{{ registrySummary.active }} / {{ registrySummary.total }}</p>
          <p class="stat-note">{{ registrySummary.inactive }} 个非 ACTIVE</p>
        </article>
        <article class="ops-summary-card">
          <p class="stat-label">累计传输</p>
          <p class="stat-value">{{ dataPlaneSummary.transferCount }}</p>
          <p class="stat-note">{{ dataPlaneSummary.up }} 个数据面在线</p>
        </article>
      </div>

      <div v-if="unhealthyServices.length" class="issue-list">
        <article v-for="item in unhealthyServices" :key="item.moduleName + item.checkName + item.endpoint" class="issue-row">
          <div>
            <p>{{ item.moduleName }} · {{ item.checkName }}</p>
            <p class="muted">{{ item.message || '未返回错误详情' }}</p>
          </div>
          <span class="state-chip err">{{ item.statusCode ?? item.status }}</span>
        </article>
      </div>

      <div class="health-grid">
        <article v-for="item in pagedServices" :key="item.moduleName + item.checkName" class="health-card">
          <div>
            <p class="muted">{{ item.moduleName }}</p>
            <p>{{ item.checkName }}</p>
            <p class="mono">{{ item.endpoint }}</p>
            <p class="muted">时间：{{ formatDateTime(item.checkedAt) }}</p>
          </div>
          <span class="state-chip" :class="statusClass(item.status)">{{ item.statusCode ?? item.status }}</span>
        </article>
      </div>
      <div v-if="services.length" class="pager">
        <button class="btn" type="button" @click="healthPage -= 1" :disabled="healthPage <= 1">上一页</button>
        <p class="muted">第 {{ healthPage }} / {{ healthPageCount }} 页 · 共 {{ sortedServices.length }} 条</p>
        <button class="btn" type="button" @click="healthPage += 1" :disabled="healthPage >= healthPageCount">下一页</button>
      </div>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="panel-head">
          <h3>控制面 Data Plane 注册</h3>
          <button class="btn" type="button" @click="loadRegistry" :disabled="registryLoading">
            {{ registryLoading ? '刷新中...' : '刷新注册表' }}
          </button>
        </div>

        <table v-if="registryItems.length" class="table">
          <thead>
            <tr>
              <th>ID</th>
              <th>协议</th>
              <th>状态</th>
              <th>Public API</th>
              <th>Control API</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedRegistryItems" :key="item.id">
              <td class="mono">{{ item.id }}</td>
              <td>{{ item.protocol }}</td>
              <td><span class="state-chip" :class="statusClass(item.status)">{{ item.status }}</span></td>
              <td class="mono">{{ item.publicApiBaseUrl }}</td>
              <td class="mono">{{ item.controlApiBaseUrl }}</td>
              <td>{{ formatDateTime(item.lastSeenAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="registryItems.length" class="pager">
          <button class="btn" type="button" @click="registryPage -= 1" :disabled="registryPage <= 1">上一页</button>
          <p class="muted">第 {{ registryPage }} / {{ registryPageCount }} 页 · 共 {{ sortedRegistryItems.length }} 条</p>
          <button class="btn" type="button" @click="registryPage += 1" :disabled="registryPage >= registryPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无 Data Plane 注册信息。</p>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>数据平面运行摘要</h3>
          <button class="btn" type="button" @click="loadDataPlaneInfos" :disabled="dataPlaneInfoLoading">
            {{ dataPlaneInfoLoading ? '加载中...' : '刷新摘要' }}
          </button>
        </div>

        <div class="dual-grid">
          <article v-for="item in pagedDataPlaneInfos" :key="item.dataPlaneId" class="step-card">
            <div class="card-title-row">
              <p class="muted">{{ item.dataPlaneId }}</p>
              <span class="state-chip" :class="statusClass(item.status)">{{ item.statusCode ?? item.status }}</span>
            </div>
            <p class="mono">{{ item.publicApiBaseUrl }}</p>
            <p>累计传输：{{ item.transferCount ?? 0 }}</p>
            <p class="mono">{{ item.message }}</p>
            <p class="muted">时间：{{ formatDateTime(item.checkedAt) }}</p>
          </article>
        </div>
        <div v-if="dataPlaneInfos.length" class="pager">
          <button class="btn" type="button" @click="dataPlaneInfoPage -= 1" :disabled="dataPlaneInfoPage <= 1">上一页</button>
          <p class="muted">第 {{ dataPlaneInfoPage }} / {{ dataPlaneInfoPageCount }} 页 · 共 {{ sortedDataPlaneInfos.length }} 条</p>
          <button class="btn" type="button" @click="dataPlaneInfoPage += 1" :disabled="dataPlaneInfoPage >= dataPlaneInfoPageCount">下一页</button>
        </div>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CONTROL_BASE, requestJson } from '../lib/http'

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

type RegistryItem = {
  id: string
  protocol: string
  status: string
  publicApiBaseUrl: string
  controlApiBaseUrl: string
  lastSeenAt: string
}

type DataPlaneInfo = {
  dataPlaneId: string
  publicApiBaseUrl: string
  transferCount: number
  statusCode: number | null
  status: string
  message: string
  checkedAt: string
}

const services = ref<MonitorStatus[]>([])
const registryItems = ref<RegistryItem[]>([])
const dataPlaneInfos = ref<DataPlaneInfo[]>([])

const healthLoading = ref(false)
const registryLoading = ref(false)
const dataPlaneInfoLoading = ref(false)
const PAGE_SIZE = 10
const healthPage = ref(1)
const registryPage = ref(1)
const dataPlaneInfoPage = ref(1)

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

const sortedServices = computed(() => [...services.value].sort((a, b) => toTimeMillis(b.checkedAt) - toTimeMillis(a.checkedAt)))
const sortedRegistryItems = computed(() => [...registryItems.value].sort((a, b) => toTimeMillis(b.lastSeenAt) - toTimeMillis(a.lastSeenAt)))
const sortedDataPlaneInfos = computed(() => [...dataPlaneInfos.value].sort((a, b) => toTimeMillis(b.checkedAt) - toTimeMillis(a.checkedAt)))

const healthSummary = computed(() => {
  const total = services.value.length
  const up = services.value.filter((item) => item.status === 'UP').length
  return {
    total,
    up,
    down: Math.max(0, total - up),
    lastCheckedAt: sortedServices.value[0]?.checkedAt ?? ''
  }
})
const registrySummary = computed(() => {
  const total = registryItems.value.length
  const active = registryItems.value.filter((item) => item.status === 'ACTIVE').length
  return {
    total,
    active,
    inactive: Math.max(0, total - active)
  }
})
const dataPlaneSummary = computed(() => {
  const up = dataPlaneInfos.value.filter((item) => item.status === 'UP').length
  const transferCount = dataPlaneInfos.value.reduce((sum, item) => sum + (item.transferCount ?? 0), 0)
  return {
    up,
    transferCount
  }
})
const unhealthyServices = computed(() => sortedServices.value.filter((item) => item.status !== 'UP').slice(0, 4))

const healthPageCount = computed(() => Math.max(1, Math.ceil(sortedServices.value.length / PAGE_SIZE)))
const registryPageCount = computed(() => Math.max(1, Math.ceil(sortedRegistryItems.value.length / PAGE_SIZE)))
const dataPlaneInfoPageCount = computed(() => Math.max(1, Math.ceil(sortedDataPlaneInfos.value.length / PAGE_SIZE)))

const pagedServices = computed(() => paginate(sortedServices.value, healthPage.value))
const pagedRegistryItems = computed(() => paginate(sortedRegistryItems.value, registryPage.value))
const pagedDataPlaneInfos = computed(() => paginate(sortedDataPlaneInfos.value, dataPlaneInfoPage.value))

function statusClass(status: string): string {
  if (status === 'UP' || status === '200' || status === 'ACTIVE') {
    return 'ok'
  }
  if (status === 'PENDING') {
    return 'pending'
  }
  return 'err'
}

async function refreshHealth() {
  healthLoading.value = true
  try {
    services.value = await requestJson<MonitorStatus[]>(`${CONTROL_BASE}/api/monitor/health`)
    healthPage.value = 1
  } catch (_err) {
    services.value = []
  } finally {
    healthLoading.value = false
  }
}

async function loadRegistry() {
  registryLoading.value = true
  try {
    registryItems.value = await requestJson<RegistryItem[]>(`${CONTROL_BASE}/api/dataplanes`)
    registryPage.value = 1
  } catch (_err) {
    registryItems.value = []
  } finally {
    registryLoading.value = false
  }
}

async function loadDataPlaneInfos() {
  dataPlaneInfoLoading.value = true
  try {
    dataPlaneInfos.value = await requestJson<DataPlaneInfo[]>(`${CONTROL_BASE}/api/monitor/dataplanes`)
    dataPlaneInfoPage.value = 1
  } catch (_err) {
    dataPlaneInfos.value = []
  } finally {
    dataPlaneInfoLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([refreshHealth(), loadRegistry(), loadDataPlaneInfos()])
})
</script>

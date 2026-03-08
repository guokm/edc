<template>
  <div class="panel panel-main">
    <div class="panel-head">
      <h3>治理与配套服务接口</h3>
      <button class="btn" type="button" @click="runChecks" :disabled="loading">
        {{ loading ? '检测中...' : '执行巡检' }}
      </button>
    </div>

    <table class="table">
      <thead>
        <tr>
          <th>服务</th>
          <th>接口</th>
          <th>状态</th>
          <th>时间</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="item in pagedChecks" :key="item.name">
          <td>{{ item.name }}</td>
          <td>{{ item.url }}</td>
          <td>
            <span :class="['status-chip', item.code === '200' ? 'ok' : item.code === '...' ? 'pending' : 'down']">
              {{ item.code }}
            </span>
          </td>
          <td>{{ formatDateTime(item.checkedAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div class="pager">
      <button class="btn" type="button" @click="page -= 1" :disabled="page <= 1">上一页</button>
      <p class="muted">第 {{ page }} / {{ pageCount }} 页 · 共 {{ sortedChecks.length }} 条</p>
      <button class="btn" type="button" @click="page += 1" :disabled="page >= pageCount">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

type CheckItem = {
  name: string
  url: string
  code: string
  checkedAt: string
}

const checks = ref<CheckItem[]>([
  { name: 'Identity DID', url: 'http://localhost:8183/api/identity/did', code: '...', checkedAt: '' },
  { name: 'Issuer Credential', url: 'http://localhost:8184/api/issuer/credentials', code: '...', checkedAt: '' },
  { name: 'Federated Catalog', url: 'http://localhost:8185/api/federated/catalog', code: '...', checkedAt: '' },
  { name: 'Operator Membership', url: 'http://localhost:8186/api/memberships', code: '...', checkedAt: '' }
])

const loading = ref(false)
const PAGE_SIZE = 10
const page = ref(1)

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

const sortedChecks = computed(() => [...checks.value].sort((a, b) => toTimeMillis(b.checkedAt) - toTimeMillis(a.checkedAt)))
const pageCount = computed(() => Math.max(1, Math.ceil(sortedChecks.value.length / PAGE_SIZE)))
const pagedChecks = computed(() => sortedChecks.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function runChecks() {
  loading.value = true

  await Promise.all(
    checks.value.map(async (item) => {
      try {
        const response = await fetch(item.url, {
          method: item.url.includes('/credentials') || item.url.includes('/memberships') ? 'POST' : 'GET',
          headers: { 'Content-Type': 'application/json' },
          body: item.url.includes('/credentials')
            ? JSON.stringify({
                type: 'MembershipCredential',
                issuer: 'issuer-A',
                claims: { level: 'GOLD' },
                expiresAt: '2026-12-31T00:00:00Z'
              })
            : item.url.includes('/memberships')
              ? JSON.stringify({ participantId: 'participant-a', level: 'GOLD', validTo: '2026-12-31T00:00:00Z' })
              : undefined
        })
        item.code = String(response.status)
      } catch (_err) {
        item.code = 'ERR'
      } finally {
        item.checkedAt = new Date().toISOString()
      }
    })
  )

  page.value = 1
  loading.value = false
}
</script>

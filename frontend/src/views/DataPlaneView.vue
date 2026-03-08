<template>
  <div class="panel panel-main">
    <div class="panel-head">
      <h3>Data Plane 注册信息</h3>
      <button class="btn" type="button" @click="loadDataPlanes" :disabled="loading">
        {{ loading ? '刷新中...' : '刷新列表' }}
      </button>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <table v-if="items.length" class="table">
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
        <tr v-for="item in pagedItems" :key="item.id">
          <td>{{ item.id }}</td>
          <td>{{ item.protocol }}</td>
          <td>{{ item.status }}</td>
          <td>{{ item.publicApiBaseUrl }}</td>
          <td>{{ item.controlApiBaseUrl }}</td>
          <td>{{ formatDateTime(item.lastSeenAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div v-if="items.length" class="pager">
      <button class="btn" type="button" @click="page -= 1" :disabled="page <= 1">上一页</button>
      <p class="muted">第 {{ page }} / {{ pageCount }} 页 · 共 {{ sortedItems.length }} 条</p>
      <button class="btn" type="button" @click="page += 1" :disabled="page >= pageCount">下一页</button>
    </div>

    <p v-else class="muted">暂无节点数据</p>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

type DataPlaneItem = {
  id: string
  protocol: string
  status: string
  publicApiBaseUrl: string
  controlApiBaseUrl: string
  lastSeenAt: string
}

const items = ref<DataPlaneItem[]>([])
const loading = ref(false)
const error = ref('')
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

const sortedItems = computed(() => [...items.value].sort((a, b) => toTimeMillis(b.lastSeenAt) - toTimeMillis(a.lastSeenAt)))
const pageCount = computed(() => Math.max(1, Math.ceil(sortedItems.value.length / PAGE_SIZE)))
const pagedItems = computed(() => sortedItems.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))

async function loadDataPlanes() {
  loading.value = true
  error.value = ''

  try {
    const response = await fetch('http://localhost:8181/api/dataplanes')
    if (!response.ok) {
      throw new Error(`查询失败：HTTP ${response.status}`)
    }
    items.value = (await response.json()) as DataPlaneItem[]
    page.value = 1
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadDataPlanes()
})
</script>

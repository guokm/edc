<template>
  <div class="view-grid">
    <div class="panel panel-main">
      <div class="panel-head">
        <h3>服务健康状态</h3>
        <button class="btn" type="button" @click="refreshHealth" :disabled="loading">
          {{ loading ? '检测中...' : '刷新状态' }}
        </button>
      </div>
      <div class="health-grid">
        <article v-for="item in services" :key="item.name" class="health-card">
          <div>
            <p class="muted">{{ item.name }}</p>
            <strong>{{ item.port }}</strong>
          </div>
          <span :class="['status-chip', item.status === 'UP' ? 'ok' : item.status === 'CHECKING' ? 'pending' : 'down']">
            {{ item.status }}
          </span>
        </article>
      </div>
    </div>

    <div class="panel">
      <h3>当前基线</h3>
      <ul class="plain-list">
        <li>Spring Boot 3.4.7</li>
        <li>MyBatis Plus 3.5.8</li>
        <li>MySQL 持久化</li>
        <li>Data Plane 双节点分流</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

type ServiceStatus = {
  name: string
  port: string
  status: string
}

const services = ref<ServiceStatus[]>([
  { name: 'Control Plane', port: '8181', status: 'CHECKING' },
  { name: 'Data Plane 1', port: '8182', status: 'CHECKING' },
  { name: 'Identity Hub', port: '8183', status: 'CHECKING' },
  { name: 'Issuer Service', port: '8184', status: 'CHECKING' },
  { name: 'Federated Catalog', port: '8185', status: 'CHECKING' },
  { name: 'Operator Services', port: '8186', status: 'CHECKING' },
  { name: 'Data Plane 2', port: '8187', status: 'CHECKING' }
])

const loading = ref(false)

async function refreshHealth() {
  loading.value = true
  await Promise.all(
    services.value.map(async (item) => {
      item.status = 'CHECKING'
      try {
        const response = await fetch(`http://localhost:${item.port}/actuator/health`)
        item.status = response.ok ? 'UP' : `HTTP-${response.status}`
      } catch (_err) {
        item.status = 'DOWN'
      }
    })
  )
  loading.value = false
}

onMounted(() => {
  void refreshHealth()
})
</script>

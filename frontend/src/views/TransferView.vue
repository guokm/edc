<template>
  <div class="view-grid">
    <div class="panel panel-main">
      <div class="panel-head">
        <h3>EDR 查询</h3>
        <button class="btn" type="button" @click="queryEdr" :disabled="loading">查询</button>
      </div>

      <div class="form-row single">
        <label>
          传输流程ID
          <input v-model="transferId" type="text" placeholder="tp-xxxx" />
        </label>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>

      <div v-if="edr" class="result-box">
        <p><strong>endpoint:</strong> {{ edr.endpoint }}</p>
        <p><strong>authToken:</strong> {{ edr.authToken }}</p>
      </div>

      <div class="form-row single">
        <label>
          请求 message
          <input v-model="message" type="text" />
        </label>
      </div>

      <button class="btn solid" type="button" :disabled="!edr || pulling" @click="pullData">
        {{ pulling ? '拉取中...' : '拉取数据' }}
      </button>

      <pre v-if="payload" class="json-view">{{ payload }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

type EdrResponse = {
  endpoint: string
  authToken: string
}

const transferId = ref('')
const message = ref('verify')
const edr = ref<EdrResponse | null>(null)
const payload = ref('')
const error = ref('')
const loading = ref(false)
const pulling = ref(false)

async function queryEdr() {
  if (!transferId.value) {
    error.value = '请先填写传输流程ID'
    return
  }

  loading.value = true
  error.value = ''
  payload.value = ''

  try {
    const response = await fetch(`http://localhost:8181/api/transfers/${transferId.value}/edr`)
    if (!response.ok) {
      throw new Error(`查询失败：HTTP ${response.status}`)
    }
    edr.value = (await response.json()) as EdrResponse
  } catch (err) {
    error.value = err instanceof Error ? err.message : '查询失败'
    edr.value = null
  } finally {
    loading.value = false
  }
}

async function pullData() {
  if (!edr.value) {
    return
  }
  pulling.value = true
  error.value = ''

  try {
    const response = await fetch(`${edr.value.endpoint}?message=${encodeURIComponent(message.value)}`, {
      headers: { Authorization: edr.value.authToken }
    })
    if (!response.ok) {
      throw new Error(`拉取失败：HTTP ${response.status}`)
    }
    payload.value = JSON.stringify(await response.json(), null, 2)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '拉取失败'
  } finally {
    pulling.value = false
  }
}
</script>

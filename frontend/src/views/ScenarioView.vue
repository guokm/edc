<template>
  <div class="panel panel-main">
    <div class="panel-head">
      <h3>控制面场景演练</h3>
      <button class="btn" type="button" @click="runScenario" :disabled="running">
        {{ running ? '执行中...' : '执行场景' }}
      </button>
    </div>

    <div class="form-row">
      <label>
        资产数量
        <input v-model.number="assetCount" type="number" min="1" max="20" />
      </label>
      <label>
        消费者ID
        <input v-model="consumerId" type="text" />
      </label>
    </div>

    <p v-if="error" class="error-text">{{ error }}</p>

    <div v-if="result" class="result-grid">
      <article class="panel">
        <h4>传输流程</h4>
        <ul class="plain-list">
          <li v-for="id in result.transferIds" :key="id">{{ id }}</li>
        </ul>
      </article>
      <article class="panel">
        <h4>落库统计</h4>
        <ul class="plain-list">
          <li v-for="(value, key) in result.rowCounts" :key="key">{{ key }}: {{ value }}</li>
        </ul>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

type ScenarioResponse = {
  transferIds: string[]
  rowCounts: Record<string, number>
}

const assetCount = ref(3)
const consumerId = ref('participant-b')
const running = ref(false)
const error = ref('')
const result = ref<ScenarioResponse | null>(null)

async function runScenario() {
  running.value = true
  error.value = ''
  result.value = null

  try {
    const response = await fetch('http://localhost:8181/api/scenario/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ assetCount: assetCount.value, consumerId: consumerId.value })
    })

    if (!response.ok) {
      throw new Error(`场景执行失败：HTTP ${response.status}`)
    }

    result.value = (await response.json()) as ScenarioResponse
  } catch (err) {
    error.value = err instanceof Error ? err.message : '场景执行失败'
  } finally {
    running.value = false
  }
}
</script>

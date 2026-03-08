<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="top-role-grid" aria-label="角色入口">
        <button
          v-for="role in roles"
          :key="role.id"
          type="button"
          class="top-role-btn role-card"
          :class="[role.id, { active: currentRole === role.id }]"
          @click="switchRole(role.id)"
        >
          <p class="role-tag">{{ role.title }}</p>
          <h4>{{ role.orgName }}（{{ role.participantId }}）</h4>
          <p>职责：{{ role.duty }}</p>
        </button>
      </div>

      <div class="bar-row">
        <p class="eyebrow">页面功能标签（再切换 tab）</p>
        <nav class="top-nav" aria-label="主导航">
          <button
            v-for="page in pages"
            :key="page.id"
            type="button"
            class="nav-tab"
            :class="{ active: activePageId === page.id }"
            @click="activePageId = page.id"
          >
            <span>{{ page.title }}</span>
            <small>{{ page.desc }}</small>
          </button>
        </nav>
      </div>

      <div class="meta-row">
        <div class="brand-zone">
          <p class="eyebrow">EDC Commercial Dataspace</p>
          <h1>模块流转可视化控制台</h1>
        </div>
        <div class="clock-pill">
          <p>{{ activeRole.title }} · {{ activeRole.orgName }}</p>
          <p>{{ now }}</p>
        </div>
      </div>
    </header>

    <main class="page-stage">
      <component :is="activePage.component" v-bind="activePage.props || {}" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import FlowStudioView from './views/FlowStudioView.vue'
import GovernanceHubView from './views/GovernanceHubView.vue'
import NodeMonitorView from './views/NodeMonitorView.vue'
import RolePlaybookView from './views/RolePlaybookView.vue'

type RoleId = 'provider' | 'consumer' | 'operator'

type PageConfig = {
  id: string
  title: string
  desc: string
  component: unknown
  props?: Record<string, unknown>
}

const roles: Array<{
  id: RoleId
  title: string
  orgName: string
  participantId: string
  duty: string
}> = [
  {
    id: 'provider',
    title: '供应方',
    orgName: '华东车联',
    participantId: 'participant-a',
    duty: '创建并发布资产、提供数据、接受协商。'
  },
  {
    id: 'consumer',
    title: '消费方',
    orgName: '保险风控中心',
    participantId: 'participant-b',
    duty: '发现目录资产、发起协商、签约后拉取数据。'
  },
  {
    id: 'operator',
    title: '运营方',
    orgName: '数据空间运营平台',
    participantId: 'operator',
    duty: '身份治理、计费校验、成员与策略管理。'
  }
]

const currentRole = ref<RoleId>('provider')

const rolePages = computed<Record<RoleId, PageConfig[]>>(() => {
  return {
    provider: [
      {
        id: 'provider-workbench',
        title: '供应方工作台',
        desc: '发布资产、查看协商、跟踪交付',
        component: FlowStudioView,
        props: { mode: 'provider' }
      },
      { id: 'provider-ops', title: '节点与健康', desc: '确认节点可用与双平面状态', component: NodeMonitorView },
      {
        id: 'provider-playbook',
        title: '角色演示文档',
        desc: '供应方 3 步演示脚本',
        component: RolePlaybookView,
        props: { role: 'provider' }
      }
    ],
    consumer: [
      {
        id: 'consumer-workbench',
        title: '消费方工作台',
        desc: '查目录、签协议、拉取数据',
        component: FlowStudioView,
        props: { mode: 'consumer' }
      },
      {
        id: 'consumer-playbook',
        title: '角色演示文档',
        desc: '消费方 3 步演示脚本',
        component: RolePlaybookView,
        props: { role: 'consumer' }
      }
    ],
    operator: [
      { id: 'operator-governance', title: '治理模块接口', desc: '四模块接口联调与计费校验', component: GovernanceHubView },
      { id: 'operator-ops', title: '节点与健康', desc: '运行态可观测与故障排查', component: NodeMonitorView },
      {
        id: 'operator-playbook',
        title: '角色演示文档',
        desc: '运营方 3 步演示脚本',
        component: RolePlaybookView,
        props: { role: 'operator' }
      }
    ]
  }
})

const pages = computed(() => rolePages.value[currentRole.value])
const activeRole = computed(() => roles.find((role) => role.id === currentRole.value) ?? roles[0])

const activePageId = ref(pages.value[0].id)
const activePage = computed(() => pages.value.find((page) => page.id === activePageId.value) ?? pages.value[0])

function switchRole(role: RoleId) {
  currentRole.value = role
}

watch(pages, (newPages) => {
  if (!newPages.some((page) => page.id === activePageId.value)) {
    activePageId.value = newPages[0].id
  }
})

const now = ref(new Date().toLocaleString())
let timer: number | undefined

onMounted(() => {
  timer = window.setInterval(() => {
    now.value = new Date().toLocaleString()
  }, 1000)
})

onUnmounted(() => {
  if (timer) {
    clearInterval(timer)
  }
})
</script>

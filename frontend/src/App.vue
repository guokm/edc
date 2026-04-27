<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="meta-row">
        <div class="brand-zone">
          <p class="eyebrow">V1.1.2 · Ops Observability</p>
          <h1>模块流转可视化控制台</h1>
          <p class="brand-subtitle">先登录，再按角色进入对应工作台；账号、参与方、协商和计费终于能讲成一条线。</p>
        </div>
        <div class="clock-pill">
          <p>{{ headerRoleLine }}</p>
          <p>{{ now }}</p>
        </div>
      </div>

      <section class="auth-banner">
        <div class="auth-copy">
          <p class="eyebrow">登录状态</p>
          <h3>{{ isAuthenticated ? '已接入运营登录态' : '请先选择一个角色账号登录' }}</h3>
          <p class="muted">
            {{ isAuthenticated ? sessionSummary : '登录成功后，顶部角色入口会自动切到当前账号所属角色，运营方还会开放组织/参与方/账号主数据门户。' }}
          </p>
        </div>

        <form v-if="!isAuthenticated" class="auth-form" @submit.prevent="submitLogin">
          <label>
            用户名
            <input v-model.trim="loginUsername" type="text" placeholder="operator_admin" />
          </label>
          <label>
            密码
            <input v-model="loginPassword" type="password" placeholder="ChangeMe@123" />
          </label>
          <div class="inline-actions auth-actions">
            <button class="btn solid" type="submit" :disabled="authState.loading || !loginUsername || !loginPassword">
              {{ authState.loading ? '登录中...' : '登录进入角色门户' }}
            </button>
          </div>
          <p v-if="authState.error" class="error-text">{{ authState.error }}</p>
        </form>

        <div v-else class="session-card">
          <p class="eyebrow">当前会话</p>
          <h3>{{ authState.session?.user.displayName }}</h3>
          <p class="muted">账号：<span class="mono">{{ authState.session?.user.username }}</span></p>
          <p class="muted">参与方：<span class="mono">{{ authState.session?.user.participantId }}</span></p>
          <p class="muted">角色：{{ authState.session?.user.roleCode }}</p>
          <p class="muted">过期：{{ formatDateTime(authState.session?.expiresAt ?? '') }}</p>
          <button class="btn" type="button" @click="handleLogout" :disabled="authState.loading">
            {{ authState.loading ? '退出中...' : '退出登录' }}
          </button>
        </div>
      </section>

      <div class="top-role-grid" aria-label="角色入口">
        <button
          v-for="role in roles"
          :key="role.id"
          type="button"
          class="top-role-btn role-card"
          :class="[role.id, { active: currentRole === role.id, locked: !canAccessRole(role.id) }]"
          :disabled="!canAccessRole(role.id)"
          @click="switchRole(role.id)"
        >
          <p class="role-tag">{{ role.title }}</p>
          <h4>{{ role.orgName }}（{{ role.participantId }}）</h4>
          <p>{{ role.duty }}</p>
          <p class="role-access">{{ roleAccessText(role.id) }}</p>
        </button>
      </div>

      <div v-if="isAuthenticated && pages.length" class="bar-row">
        <p class="eyebrow">角色内导航</p>
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
    </header>

    <main class="page-stage">
      <section v-if="!isAuthenticated" class="panel login-gate">
        <div class="panel-head">
          <h3>角色账号卡片</h3>
        </div>
        <div class="account-grid">
          <button
            v-for="account in demoAccounts"
            :key="account.username"
            type="button"
            class="account-card"
            @click="applyDemoAccount(account.role)"
          >
            <p class="role-tag">{{ account.roleLabel }}</p>
            <h4>{{ account.orgName }}</h4>
            <p class="muted">用户名：<span class="mono">{{ account.username }}</span></p>
            <p class="muted">参与方：<span class="mono">{{ account.participantId }}</span></p>
            <p>{{ account.note }}</p>
          </button>
        </div>
      </section>

      <component v-else :is="activePage.component" v-bind="activePage.props || {}" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import {
  authState,
  demoAccounts,
  initializeOperatorAuth,
  isAuthenticated,
  loginOperator,
  logoutOperator,
  sessionRole,
  useDemoAccount,
  type RoleId
} from './lib/operatorAuth'
import FlowStudioView from './views/FlowStudioView.vue'
import GovernanceHubView from './views/GovernanceHubView.vue'
import NodeMonitorView from './views/NodeMonitorView.vue'
import OperatorPortalView from './views/OperatorPortalView.vue'
import RolePlaybookView from './views/RolePlaybookView.vue'

type PageConfig = {
  id: string
  title: string
  desc: string
  component: unknown
  props?: Record<string, unknown>
}

initializeOperatorAuth()

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
const loginUsername = ref('')
const loginPassword = ref('')

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
      { id: 'operator-portal', title: '运营主数据', desc: '组织、参与方、账号绑定关系', component: OperatorPortalView },
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

const pages = computed(() => {
  if (!isAuthenticated.value) {
    return []
  }
  return rolePages.value[currentRole.value]
})

const activePageId = ref('')
const activePage = computed(() => pages.value.find((page) => page.id === activePageId.value) ?? pages.value[0])
const activeRole = computed(() => roles.find((role) => role.id === currentRole.value) ?? roles[0])
const headerRoleLine = computed(() => {
  if (!isAuthenticated.value) {
    return '未登录 · 请选择角色账号'
  }
  return `${activeRole.value.title} · ${activeRole.value.orgName}`
})

const sessionSummary = computed(() => {
  const user = authState.session?.user
  if (!user) {
    return ''
  }
  return `${user.displayName} / ${user.username} / ${user.participantId} / ${user.roleCode}`
})

function canAccessRole(roleId: RoleId): boolean {
  return isAuthenticated.value && sessionRole.value === roleId
}

function roleAccessText(roleId: RoleId): string {
  if (!isAuthenticated.value) {
    return '请先登录对应账号'
  }
  return sessionRole.value === roleId ? '当前账号可进入' : '请切换对应角色账号'
}

function switchRole(roleId: RoleId) {
  if (!canAccessRole(roleId)) {
    return
  }
  currentRole.value = roleId
}

function applyDemoAccount(roleId: RoleId) {
  const account = useDemoAccount(roleId)
  loginUsername.value = account.username
  loginPassword.value = account.password
}

async function submitLogin() {
  try {
    await loginOperator(loginUsername.value, loginPassword.value)
  } catch (_err) {
    // 错误信息已写入 authState.error，这里不重复抛出。
  }
}

async function handleLogout() {
  await logoutOperator()
}

watch(
  () => sessionRole.value,
  (role) => {
    if (!role) {
      activePageId.value = ''
      return
    }
    currentRole.value = role
  },
  { immediate: true }
)

watch(
  pages,
  (newPages) => {
    if (!newPages.length) {
      activePageId.value = ''
      return
    }
    if (!newPages.some((page) => page.id === activePageId.value)) {
      activePageId.value = newPages[0].id
    }
  },
  { immediate: true }
)

const now = ref(new Date().toLocaleString())
let timer: number | undefined

function formatDateTime(value: string): string {
  if (!value) {
    return '-'
  }
  const parsed = Date.parse(value.replace(' ', 'T'))
  if (Number.isNaN(parsed)) {
    return value
  }
  return new Date(parsed).toLocaleString()
}

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

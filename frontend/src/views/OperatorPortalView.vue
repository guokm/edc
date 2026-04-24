<template>
  <div class="view-stack">
    <section class="panel">
      <div class="panel-head">
        <h3>运营登录与主数据门户</h3>
        <div class="inline-actions">
          <button class="btn" type="button" @click="loadAll" :disabled="loadingAny || !authState.session">
            {{ loadingAny ? '刷新中...' : '刷新全部' }}
          </button>
        </div>
      </div>

      <div v-if="authState.session" class="summary-strip">
        <article class="mini-stat">
          <p class="muted">当前用户</p>
          <p>{{ authState.session.user.displayName }}</p>
          <p class="mono">{{ authState.session.user.username }}</p>
        </article>
        <article class="mini-stat">
          <p class="muted">组织 / 参与方</p>
          <p>{{ organizationLabel }}</p>
          <p class="mono">{{ authState.session.user.participantId }}</p>
        </article>
        <article class="mini-stat">
          <p class="muted">角色 / 过期时间</p>
          <p>{{ authState.session.user.roleCode }}</p>
          <p class="mono">{{ formatDateTime(authState.session.expiresAt) }}</p>
        </article>
      </div>

      <div class="hint-box">
        <p><strong>这一页讲什么：</strong>组织是企业主体，参与方是数据空间里的业务身份，账号是实际登录人。</p>
        <p class="muted">演示时按“组织 -> 参与方 -> 账号”的顺序讲，能最快讲清会员、签发、协商和计费最后落到谁身上。</p>
      </div>
    </section>

    <section class="stat-grid compact">
      <article class="stat-card">
        <p class="stat-label">组织数</p>
        <p class="stat-value">{{ organizations.length }}</p>
        <p class="stat-note">企业/平台主体</p>
      </article>
      <article class="stat-card">
        <p class="stat-label">参与方数</p>
        <p class="stat-value">{{ participants.length }}</p>
        <p class="stat-note">控制面/协商链路 participantId</p>
      </article>
      <article class="stat-card">
        <p class="stat-label">账号数</p>
        <p class="stat-value">{{ users.length }}</p>
        <p class="stat-note">登录态与角色权限入口</p>
      </article>
      <article class="stat-card">
        <p class="stat-label">当前账号范围</p>
        <p class="stat-value">{{ authState.session?.user.participantId ?? '-' }}</p>
        <p class="stat-note">用来解释“一个账号代表谁”</p>
      </article>
    </section>

    <section class="grid-2">
      <article class="panel">
        <div class="panel-head">
          <h3>组织列表</h3>
          <button class="btn" type="button" @click="loadOrganizations" :disabled="organizationsLoading">
            {{ organizationsLoading ? '加载中...' : '刷新组织' }}
          </button>
        </div>
        <p v-if="error" class="error-text">{{ error }}</p>
        <table v-if="organizations.length" class="table">
          <thead>
            <tr>
              <th>组织ID</th>
              <th>组织名称</th>
              <th>信用代码</th>
              <th>状态</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedOrganizations" :key="item.id">
              <td class="mono">{{ item.id }}</td>
              <td>{{ item.name }}</td>
              <td class="mono">{{ item.creditCode || '-' }}</td>
              <td>{{ item.status }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="organizations.length" class="pager">
          <button class="btn" type="button" @click="organizationPage -= 1" :disabled="organizationPage <= 1">上一页</button>
          <p class="muted">第 {{ organizationPage }} / {{ organizationPageCount }} 页 · 共 {{ sortedOrganizations.length }} 条</p>
          <button class="btn" type="button" @click="organizationPage += 1" :disabled="organizationPage >= organizationPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无组织数据。</p>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h3>参与方列表</h3>
          <button class="btn" type="button" @click="loadParticipants" :disabled="participantsLoading">
            {{ participantsLoading ? '加载中...' : '刷新参与方' }}
          </button>
        </div>
        <table v-if="participants.length" class="table">
          <thead>
            <tr>
              <th>participantId</th>
              <th>展示名称</th>
              <th>所属组织</th>
              <th>角色类型</th>
              <th>时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pagedParticipants" :key="item.id">
              <td class="mono">{{ item.participantId }}</td>
              <td>{{ item.displayName }}</td>
              <td class="mono">{{ item.organizationId }}</td>
              <td>{{ item.roleType }}</td>
              <td>{{ formatDateTime(item.createdAt) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="participants.length" class="pager">
          <button class="btn" type="button" @click="participantPage -= 1" :disabled="participantPage <= 1">上一页</button>
          <p class="muted">第 {{ participantPage }} / {{ participantPageCount }} 页 · 共 {{ sortedParticipants.length }} 条</p>
          <button class="btn" type="button" @click="participantPage += 1" :disabled="participantPage >= participantPageCount">下一页</button>
        </div>
        <p v-else class="muted">暂无参与方数据。</p>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h3>账号列表</h3>
        <button class="btn" type="button" @click="loadUsers" :disabled="usersLoading">
          {{ usersLoading ? '加载中...' : '刷新账号' }}
        </button>
      </div>
      <table v-if="users.length" class="table">
        <thead>
          <tr>
            <th>用户名</th>
            <th>显示名</th>
            <th>组织ID</th>
            <th>participantId</th>
            <th>角色编码</th>
            <th>时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in pagedUsers" :key="item.id">
            <td class="mono">{{ item.username }}</td>
            <td>{{ item.displayName }}</td>
            <td class="mono">{{ item.organizationId }}</td>
            <td class="mono">{{ item.participantId }}</td>
            <td>{{ item.roleCode }}</td>
            <td>{{ formatDateTime(item.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
      <div v-if="users.length" class="pager">
        <button class="btn" type="button" @click="userPage -= 1" :disabled="userPage <= 1">上一页</button>
        <p class="muted">第 {{ userPage }} / {{ userPageCount }} 页 · 共 {{ sortedUsers.length }} 条</p>
        <button class="btn" type="button" @click="userPage += 1" :disabled="userPage >= userPageCount">下一页</button>
      </div>
      <p v-else class="muted">暂无账号数据。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { authState } from '../lib/operatorAuth'
import { OPERATOR_BASE, requestJson } from '../lib/http'

type ListResponse<T> = {
  items: T[]
}

type OrganizationItem = {
  id: string
  name: string
  creditCode: string
  contactName: string
  contactPhone: string
  contactEmail: string
  status: string
  createdAt: string
  updatedAt: string
}

type ParticipantItem = {
  id: string
  participantId: string
  organizationId: string
  displayName: string
  roleType: string
  status: string
  createdAt: string
  updatedAt: string
}

type UserItem = {
  id: string
  username: string
  displayName: string
  organizationId: string
  participantId: string
  roleCode: string
  status: string
  createdAt: string
  updatedAt: string
}

const PAGE_SIZE = 10

const organizations = ref<OrganizationItem[]>([])
const participants = ref<ParticipantItem[]>([])
const users = ref<UserItem[]>([])

const organizationsLoading = ref(false)
const participantsLoading = ref(false)
const usersLoading = ref(false)
const error = ref('')

const organizationPage = ref(1)
const participantPage = ref(1)
const userPage = ref(1)

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
  return items.slice((page - 1) * PAGE_SIZE, page * PAGE_SIZE)
}

const sortedOrganizations = computed(() => [...organizations.value].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt)))
const sortedParticipants = computed(() => [...participants.value].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt)))
const sortedUsers = computed(() => [...users.value].sort((a, b) => toTimeMillis(b.createdAt) - toTimeMillis(a.createdAt)))

const organizationPageCount = computed(() => Math.max(1, Math.ceil(sortedOrganizations.value.length / PAGE_SIZE)))
const participantPageCount = computed(() => Math.max(1, Math.ceil(sortedParticipants.value.length / PAGE_SIZE)))
const userPageCount = computed(() => Math.max(1, Math.ceil(sortedUsers.value.length / PAGE_SIZE)))

const pagedOrganizations = computed(() => paginate(sortedOrganizations.value, organizationPage.value))
const pagedParticipants = computed(() => paginate(sortedParticipants.value, participantPage.value))
const pagedUsers = computed(() => paginate(sortedUsers.value, userPage.value))

const loadingAny = computed(() => organizationsLoading.value || participantsLoading.value || usersLoading.value)
const organizationLabel = computed(() => authState.session?.user.organizationId ?? '-')

async function loadOrganizations() {
  if (!authState.session) {
    return
  }
  organizationsLoading.value = true
  error.value = ''
  try {
    const data = await requestJson<ListResponse<OrganizationItem>>(`${OPERATOR_BASE}/api/organizations`)
    organizations.value = data.items ?? []
    organizationPage.value = 1
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载组织失败'
    organizations.value = []
  } finally {
    organizationsLoading.value = false
  }
}

async function loadParticipants() {
  if (!authState.session) {
    return
  }
  participantsLoading.value = true
  error.value = ''
  try {
    const data = await requestJson<ListResponse<ParticipantItem>>(`${OPERATOR_BASE}/api/participants`)
    participants.value = data.items ?? []
    participantPage.value = 1
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载参与方失败'
    participants.value = []
  } finally {
    participantsLoading.value = false
  }
}

async function loadUsers() {
  if (!authState.session) {
    return
  }
  usersLoading.value = true
  error.value = ''
  try {
    const data = await requestJson<ListResponse<UserItem>>(`${OPERATOR_BASE}/api/users`)
    users.value = data.items ?? []
    userPage.value = 1
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载账号失败'
    users.value = []
  } finally {
    usersLoading.value = false
  }
}

async function loadAll() {
  await Promise.all([loadOrganizations(), loadParticipants(), loadUsers()])
}

watch(
  () => authState.session?.token,
  (token) => {
    if (token) {
      void loadAll()
      return
    }
    organizations.value = []
    participants.value = []
    users.value = []
  }
)

onMounted(() => {
  if (authState.session) {
    void loadAll()
  }
})
</script>

import { computed, reactive } from 'vue'
import { OPERATOR_BASE, registerOperatorTokenResolver, requestJson } from './http'

export type RoleId = 'provider' | 'consumer' | 'operator'

type SessionUser = {
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

type LoginResponse = {
  token: string
  user: SessionUser
  expiresAt: string
}

type StoredSession = LoginResponse

type DemoAccount = {
  role: RoleId
  roleLabel: string
  username: string
  password: string
  orgName: string
  participantId: string
  note: string
}

const STORAGE_KEY = 'edc-operator-session-v1'

export const demoAccounts: DemoAccount[] = [
  {
    role: 'provider',
    roleLabel: '供应方',
    username: 'provider_admin',
    password: 'ChangeMe@123',
    orgName: '华东车联',
    participantId: 'participant-a',
    note: '用于资产发布、查看协商与追踪交付。'
  },
  {
    role: 'consumer',
    roleLabel: '消费方',
    username: 'consumer_admin',
    password: 'ChangeMe@123',
    orgName: '保险风控中心',
    participantId: 'participant-b',
    note: '用于目录发现、协商签约、发起传输。'
  },
  {
    role: 'operator',
    roleLabel: '运营方',
    username: 'operator_admin',
    password: 'ChangeMe@123',
    orgName: '数据空间运营平台',
    participantId: 'operator',
    note: '用于成员治理、接口巡检、计费与审计。'
  }
]

export const authState = reactive<{
  session: StoredSession | null
  loading: boolean
  hydrated: boolean
  error: string
}>({
  session: null,
  loading: false,
  hydrated: false,
  error: ''
})

registerOperatorTokenResolver(() => authState.session?.token ?? '')

export const isAuthenticated = computed(() => Boolean(authState.session?.token))

export const sessionRole = computed<RoleId | null>(() => {
  const participantId = authState.session?.user.participantId
  if (participantId === 'participant-a') {
    return 'provider'
  }
  if (participantId === 'participant-b') {
    return 'consumer'
  }
  if (participantId === 'operator') {
    return 'operator'
  }
  return null
})

export function initializeOperatorAuth() {
  if (authState.hydrated || typeof window === 'undefined') {
    return
  }
  authState.hydrated = true
  const raw = window.localStorage.getItem(STORAGE_KEY)
  if (!raw) {
    return
  }
  try {
    const stored = JSON.parse(raw) as StoredSession
    if (!stored?.token || !stored?.user) {
      clearStoredSession()
      return
    }
    if (stored.expiresAt && Date.parse(stored.expiresAt) <= Date.now()) {
      clearStoredSession()
      return
    }
    authState.session = stored
  } catch (_err) {
    clearStoredSession()
  }
}

export async function loginOperator(username: string, password: string) {
  authState.loading = true
  authState.error = ''
  try {
    const response = await requestJson<LoginResponse>(`${OPERATOR_BASE}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    })
    authState.session = response
    persistSession(response)
    return response
  } catch (err) {
    authState.error = err instanceof Error ? err.message : '登录失败'
    throw err
  } finally {
    authState.loading = false
  }
}

export async function logoutOperator() {
  authState.loading = true
  if (!authState.session) {
    clearStoredSession()
    authState.loading = false
    return
  }
  try {
    await requestJson(`${OPERATOR_BASE}/api/auth/logout`, {
      method: 'POST'
    })
  } catch (_err) {
    // 退出失败不阻塞前端清会话。
  } finally {
    clearStoredSession()
    authState.loading = false
  }
}

export function useDemoAccount(role: RoleId) {
  const account = demoAccounts.find((item) => item.role === role) ?? demoAccounts[0]
  return {
    username: account.username,
    password: account.password
  }
}

function persistSession(session: StoredSession) {
  if (typeof window === 'undefined') {
    return
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

function clearStoredSession() {
  authState.session = null
  authState.error = ''
  if (typeof window !== 'undefined') {
    window.localStorage.removeItem(STORAGE_KEY)
  }
}

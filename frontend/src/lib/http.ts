type OperatorTokenResolver = () => string | null | undefined

let operatorTokenResolver: OperatorTokenResolver | null = null

export function registerOperatorTokenResolver(resolver: OperatorTokenResolver) {
  operatorTokenResolver = resolver
}

export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const headers = new Headers(init?.headers ?? {})
  if (url.startsWith(OPERATOR_BASE)) {
    const token = operatorTokenResolver?.()
    if (token && !headers.has('X-Operator-Token')) {
      headers.set('X-Operator-Token', token)
    }
  }

  const response = await fetch(url, {
    ...init,
    headers
  })
  const raw = await response.text()

  if (!response.ok) {
    throw new Error(raw ? `HTTP ${response.status}: ${raw}` : `HTTP ${response.status}`)
  }

  if (!raw) {
    return {} as T
  }

  try {
    return JSON.parse(raw) as T
  } catch (_err) {
    return raw as T
  }
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2)
}

export const CONTROL_BASE = '/cp'
export const IDENTITY_BASE = '/ih'
export const ISSUER_BASE = '/is'
export const FEDERATED_BASE = '/fc'
export const OPERATOR_BASE = '/op'

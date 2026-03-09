export async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init)
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

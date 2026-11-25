export type Role = 'GUEST' | 'CUSTOMER' | 'ADMIN'
const API = '/api'

export function token() {
  return sessionStorage.getItem('serviceflow_token')
}

export function parseSseData(data: string): unknown {
  try {
    return JSON.parse(data)
  } catch {
    return data
  }
}

export class StreamRequestError extends Error {
  constructor(
    message: string,
    readonly httpStatus: number,
    readonly retryable: boolean,
    readonly code?: string,
    readonly traceId?: string,
  ) {
    super(message)
  }
}

function streamError(data: unknown, status: number) {
  const detail =
    data && typeof data === 'object'
      ? (data as { message?: unknown; retryable?: unknown; code?: unknown; traceId?: unknown })
      : {}
  const reason: Record<number, string> = {
    400: '请求参数无效',
    401: '登录已失效，请重新登录',
    403: '当前账号无权访问该会话',
    404: '会话不存在或已失效',
    429: '请求过于频繁，请稍后再试',
  }
  return new StreamRequestError(
    typeof detail.message === 'string' && detail.message
      ? detail.message
      : reason[status] || '客服连接暂时不可用',
    status,
    status === 429 ||
      (typeof detail.retryable === 'boolean' ? detail.retryable : status >= 500 || status === 408),
    typeof detail.code === 'string' ? detail.code : undefined,
    typeof detail.traceId === 'string' ? detail.traceId : undefined,
  )
}

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers)
  if (!(options.body instanceof FormData)) headers.set('Content-Type', 'application/json')
  if (token()) headers.set('Authorization', `Bearer ${token()}`)
  const response = await fetch(API + path, { ...options, headers })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(error.message || '请求失败')
  }
  if (response.status === 204) return undefined as T
  return response.json()
}

export async function stream(
  path: string,
  body: unknown,
  onEvent: (name: string, data: any) => void,
  signal?: AbortSignal,
) {
  const response = await fetch(API + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
    body: JSON.stringify(body),
    signal,
  })
  if (!response.ok) throw streamError(await response.json().catch(() => undefined), response.status)
  if (!response.body) throw new StreamRequestError('无法读取客服回复', response.status, true)
  const reader = response.body.getReader(),
    decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) return
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        let name = 'message'
        const lines: string[] = []
        for (const line of block.split(/\r?\n/)) {
          if (line.startsWith('event:')) name = line.slice(6).trim()
          if (line.startsWith('data:')) lines.push(line.slice(5).replace(/^ /, ''))
        }
        if (lines.length === 0) continue
        const text = lines.join('\n')
        const data = name === 'token' ? text : parseSseData(text)
        if (name === 'error') throw streamError(data, response.status)
        onEvent(name, data)
        if (name === 'done' && data && typeof data === 'object') return
      }
    }
  } finally {
    void reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}

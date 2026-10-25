// One cancellation marker in this tab. It is neither a credential nor permission to execute.
export const PENDING_CANCELLATION_KEY = 'serviceflow_pending_cancellation_v1'
export type PendingCancellation = {
  version: 1
  owner: string
  actionId: string
  orderNo: string
  sessionId: string
}
const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i

export function cancellationOwner(token: string | null): string | null {
  try {
    if (!token || token.length > 16000) return null
    const encoded = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(
      new TextDecoder().decode(Uint8Array.from(atob(encoded), (c) => c.charCodeAt(0))),
    )
    if (
      payload.role !== 'CUSTOMER' ||
      typeof payload.sub !== 'string' ||
      !payload.sub ||
      payload.sub.length > 200 ||
      !Number.isSafeInteger(payload.customerId) ||
      payload.customerId < 1
    )
      return null
    return JSON.stringify([payload.sub, payload.customerId])
  } catch {
    return null
  }
}

export function currentCancellationOwner(): string | null {
  try {
    return cancellationOwner(sessionStorage.getItem('serviceflow_token'))
  } catch {
    return null
  }
}

export function clearPendingCancellation(
  expected?: Pick<PendingCancellation, 'owner' | 'actionId'>,
): boolean {
  try {
    if (expected) {
      const raw = sessionStorage.getItem(PENDING_CANCELLATION_KEY)
      if (raw !== null) {
        const value = JSON.parse(raw)
        if (value.owner !== expected.owner || value.actionId !== expected.actionId) return true
      }
    }
    sessionStorage.removeItem(PENDING_CANCELLATION_KEY)
    return true
  } catch {
    return false
  }
}

export function savePendingCancellation(value: PendingCancellation): boolean {
  try {
    // Pick only these fields; never serialize an action object containing queries or a response.
    const { version, owner, actionId, orderNo, sessionId } = value
    const encoded = JSON.stringify({ version, owner, actionId, orderNo, sessionId })
    if (encoded.length > 1200) return false
    sessionStorage.setItem(PENDING_CANCELLATION_KEY, encoded)
    return true
  } catch {
    return false
  }
}

export function loadPendingCancellation(owner: string | null): {
  pending?: PendingCancellation
  warning?: string
} {
  try {
    const raw = sessionStorage.getItem(PENDING_CANCELLATION_KEY)
    if (raw === null) return {}
    if (!owner) return { warning: '当前身份无法核验，未恢复取消记录。请登录原客户账号后查询订单。' }
    let value: PendingCancellation
    try {
      if (raw.length > 1200) throw new Error('oversized marker')
      value = JSON.parse(raw)
      if (
        !value ||
        value.version !== 1 ||
        typeof value.owner !== 'string' ||
        !uuid.test(value.actionId) ||
        !uuid.test(value.sessionId) ||
        !/^SF\d{12,40}$/.test(value.orderNo) ||
        Object.keys(value).sort().join(',') !== 'actionId,orderNo,owner,sessionId,version'
      )
        throw new Error('invalid marker')
    } catch {
      clearPendingCancellation()
      return {
        warning: '待核验记录已损坏，无法自动恢复；不会重新执行取消，请查看订单或联系人工核验。',
      }
    }
    if (value.owner !== owner) {
      clearPendingCancellation()
      return {}
    }
    return { pending: value }
  } catch {
    return {
      warning: '无法读取会话存储，不能确认是否有待核验取消记录；请查看订单，不要重复提交取消。',
    }
  }
}

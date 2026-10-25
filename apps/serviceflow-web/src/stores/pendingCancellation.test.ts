import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  cancellationOwner,
  clearPendingCancellation,
  loadPendingCancellation,
  PENDING_CANCELLATION_KEY,
  savePendingCancellation,
  type PendingCancellation,
} from './pendingCancellation'

describe('single identity-scoped cancellation marker', () => {
  let values: Map<string, string>
  let storage: {
    getItem: ReturnType<typeof vi.fn>
    setItem: ReturnType<typeof vi.fn>
    removeItem: ReturnType<typeof vi.fn>
  }
  const marker: PendingCancellation = {
    version: 1,
    owner: '["customer-a",1]',
    actionId: '12345678-1234-1234-1234-123456789012',
    orderNo: 'SF202610250001',
    sessionId: '12345678-1234-1234-1234-123456789013',
  }
  beforeEach(() => {
    values = new Map()
    storage = {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => {
        values.set(key, value)
      }),
      removeItem: vi.fn((key: string) => {
        values.delete(key)
      }),
    }
    vi.stubGlobal('sessionStorage', storage)
  })
  afterEach(() => vi.unstubAllGlobals())
  it('derives identity without persisting the token or treating it as server authorization', () => {
    const payload = btoa(JSON.stringify({ sub: 'customer-a', role: 'CUSTOMER', customerId: 1 }))
    expect(cancellationOwner(`header.${payload}.signature`)).toBe(marker.owner)
    expect(cancellationOwner('broken')).toBeNull()
    expect(
      cancellationOwner(
        `header.${btoa(JSON.stringify({ sub: 'admin', role: 'ADMIN', customerId: 1 }))}.signature`,
      ),
    ).toBeNull()
  })
  it('keeps only bounded marker fields and restores the same identity', () => {
    expect(savePendingCancellation({ ...marker, owner: 'x'.repeat(1201) })).toBe(false)
    expect(values.has(PENDING_CANCELLATION_KEY)).toBe(false)
    const input = {
      ...marker,
      query: 'private question',
      accessToken: 'not-to-be-saved',
      answer: 'private response',
    }
    expect(savePendingCancellation(input)).toBe(true)
    expect(JSON.parse(values.get(PENDING_CANCELLATION_KEY)!)).toEqual(marker)
    expect(loadPendingCancellation(marker.owner)).toEqual({ pending: marker })
  })
  it('does not reveal a previous account marker', () => {
    savePendingCancellation(marker)
    expect(loadPendingCancellation('["customer-b",2]')).toEqual({})
    expect(values.has(PENDING_CANCELLATION_KEY)).toBe(false)
  })
  it('old completion cannot clear another identity or newer action', () => {
    const next = { ...marker, owner: '["customer-b",2]' }
    savePendingCancellation(next)
    expect(clearPendingCancellation(marker)).toBe(true)
    expect(loadPendingCancellation(next.owner).pending).toEqual(next)
  })
  it.each([
    '{',
    'null',
    JSON.stringify({ ...marker, version: 2 }),
    JSON.stringify({ ...marker, actionId: 'bad' }),
    JSON.stringify({ ...marker, answer: 'unexpected' }),
    'x'.repeat(1201),
  ])('reports corrupt data without restoring or executing it', (raw) => {
    values.set(PENDING_CANCELLATION_KEY, raw)
    expect(loadPendingCancellation(marker.owner).warning).toContain('损坏')
    expect(values.has(PENDING_CANCELLATION_KEY)).toBe(false)
  })
  it('does not claim persistence when storage fails', () => {
    storage.setItem.mockImplementation(() => {
      throw new Error('quota')
    })
    expect(savePendingCancellation(marker)).toBe(false)
    storage.getItem.mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(loadPendingCancellation(marker.owner).warning).toContain('无法读取')
    expect(clearPendingCancellation(marker)).toBe(false)
  })
})

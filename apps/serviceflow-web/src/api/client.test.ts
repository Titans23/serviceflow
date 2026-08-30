import { describe, expect, it } from 'vitest'
import { parseSseData } from './client'

describe('parseSseData', () => {
  it('parses structured SSE payloads', () => {
    expect(parseSseData('{"intent":"PRODUCT_QUERY"}')).toEqual({ intent: 'PRODUCT_QUERY' })
  })

  it('keeps token events as plain text', () => {
    expect(parseSseData('ServicePhone A100（A100）')).toBe('ServicePhone A100（A100）')
  })
})

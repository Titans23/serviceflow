import { afterEach, describe, expect, it, vi } from 'vitest'
import { parseSseData, stream, StreamRequestError } from './client'

describe('parseSseData', () => {
  it('parses structured SSE payloads', () => {
    expect(parseSseData('{"intent":"PRODUCT_QUERY"}')).toEqual({ intent: 'PRODUCT_QUERY' })
  })

  it('keeps token events as plain text', () => {
    expect(parseSseData('ServicePhone A100（A100）')).toBe('ServicePhone A100（A100）')
  })
})

describe('chat SSE transport', () => {
  afterEach(() => vi.unstubAllGlobals())

  function respond(response: Response) {
    vi.stubGlobal('sessionStorage', { getItem: () => 'test-token' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
  }

  it('preserves token whitespace and line breaks across UTF-8 chunks', async () => {
    const encoded = new TextEncoder().encode(
      'event: token\r\ndata:   中文  \r\ndata: \r\ndata:  字\r\n\r\nevent: done\r\ndata: {}\r\n\r\n',
    )
    const body = new ReadableStream({
      start(controller) {
        for (const byte of encoded) controller.enqueue(Uint8Array.of(byte))
        // A terminal event can finish the consumer before the transport closes.
      },
    })
    respond(new Response(body))
    const events = vi.fn()
    await stream('/chat', {}, events)
    expect(events.mock.calls).toEqual([
      ['token', '  中文  \n\n 字'],
      ['done', {}],
    ])
  })

  it('keeps the server error code and retryability from an SSE error', async () => {
    respond(
      new Response(
        'event: error\ndata: {"code":"REQUEST_IN_PROGRESS","message":"处理中","retryable":true}\n\n',
      ),
    )
    await expect(stream('/chat', {}, vi.fn())).rejects.toMatchObject({
      code: 'REQUEST_IN_PROGRESS',
      message: '处理中',
      retryable: true,
      httpStatus: 200,
    })
  })

  it('delivers a complete token before propagating a later read failure', async () => {
    const body = new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('event: token\ndata: 半截\n\n'))
      },
      pull(controller) {
        controller.error(new Error('read failed'))
      },
    })
    respond(new Response(body))
    const events = vi.fn()
    await expect(stream('/chat', {}, events)).rejects.toThrow('read failed')
    expect(events.mock.calls).toEqual([['token', '半截']])
  })

  it('keeps the HTTP status and message from a rejected connection', async () => {
    respond(
      new Response('{"code":"429","message":"访客咨询过于频繁","retryable":false}', {
        status: 429,
      }),
    )
    await expect(stream('/chat', {}, vi.fn())).rejects.toMatchObject({
      code: '429',
      message: '访客咨询过于频繁',
      httpStatus: 429,
      retryable: true,
    })
    respond(new Response(null, { status: 401 }))
    await expect(stream('/chat', {}, vi.fn())).rejects.toBeInstanceOf(StreamRequestError)
  })
})

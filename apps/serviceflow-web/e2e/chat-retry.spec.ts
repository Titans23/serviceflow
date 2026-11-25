import { expect, test, type Page } from '@playwright/test'

const composer = '咨询商品、订单、物流、退款或售后政策'
const event = (name: string, data: unknown) =>
  `event: ${name}\ndata: ${name === 'token' ? data : JSON.stringify(data)}\n\n`

async function openChat(page: Page, history: unknown[] = []) {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'test-customer')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: {} } }))
  await page.route('**/api/chat/sessions', (route) =>
    route.fulfill({ json: [{ publicId: 'chat-1', title: '商品咨询' }] }),
  )
  await page.route('**/api/chat/sessions/chat-1/messages', (route) =>
    route.fulfill({ json: history }),
  )
  await page.goto('/chat?sessionId=chat-1&productId=29')
  await expect(page.locator('.session.active')).toHaveText('商品咨询')
}

test('early EOF and in-progress retry keep the same bubble, payload and cards', async ({
  page,
}) => {
  const submitted: unknown[] = []
  const action = {
    actionId: 'action-1',
    actionType: 'CANCEL_ORDER',
    orderNo: 'SF202611030001',
    message: '请确认取消',
  }
  await page.route('**/api/chat/sessions/chat-1/messages/stream', async (route) => {
    submitted.push(route.request().postDataJSON())
    const body =
      submitted.length === 1
        ? event('token', '半截回答') +
          event('meta', { intent: 'PRODUCT_QUERY' }) +
          event('action_required', action)
        : submitted.length === 2
          ? event('error', {
              code: 'REQUEST_IN_PROGRESS',
              message: '请求正在处理中',
              retryable: true,
            })
          : event('token', '完整回答') +
            event('action_required', action) +
            event('done', { messageId: 'saved-1', replayed: true })
    await route.fulfill({ contentType: 'text/event-stream', body })
  })
  await openChat(page)
  const field = page.getByPlaceholder(composer)
  await field.fill('查询这个商品')
  await field.press('Enter')

  await expect(page.getByText('回复未完整接收。', { exact: false })).toBeVisible()
  await expect(page.getByText('半截回答')).toBeVisible()
  await expect(page.getByText('请确认取消')).toHaveCount(0)
  await page.getByRole('button', { name: '重试本轮' }).click()
  await expect(page.getByText('服务端仍在处理本轮', { exact: false })).toBeVisible()
  await expect(page.getByText('半截回答')).toBeVisible()

  await page.getByRole('button', { name: '重试本轮' }).click()
  await expect(page.getByText('完整回答')).toBeVisible()
  await expect(page.getByText('请确认取消')).toBeVisible()
  await expect(page.getByText('半截回答')).toHaveCount(0)
  await expect(page.locator('.message.USER')).toHaveCount(1)
  await expect(page.locator('.message.ASSISTANT')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '重试本轮' })).toHaveCount(0)
  expect(submitted).toHaveLength(3)
  expect(submitted[0]).toMatchObject({ pageContext: { productId: 29 } })
  expect(submitted.slice(1)).toEqual([submitted[0], submitted[0]])
})

test('model failure can retry a comparison selection in the same bubble', async ({ page }) => {
  const pending = {
    comparisonId: 'comparison-1',
    targets: [
      { label: '手机 A', confirmed: { id: 1, name: '手机 A', sku: 'A-01' }, candidates: [] },
      { label: '手机 B', confirmed: { id: 2, name: '手机 B', sku: 'B-01' }, candidates: [] },
      { label: '第三款', confirmed: null, candidates: [{ id: 3, name: '手机 C', sku: 'C-01' }] },
    ],
  }
  const submitted: unknown[] = []
  await page.route('**/api/chat/sessions/chat-1/messages/stream', (route) => {
    submitted.push(route.request().postDataJSON())
    const body =
      submitted.length === 1
        ? event('error', { code: 'MODEL_UNAVAILABLE', message: '模型暂时不可用', retryable: true })
        : event('token', '已恢复回复') + event('done', { messageId: 'comparison-recovered' })
    return route.fulfill({ contentType: 'text/event-stream', body })
  })
  await openChat(page, [
    {
      role: 'ASSISTANT',
      content: '请选择第三款',
      metadataJson: JSON.stringify({ pendingComparison: pending }),
    },
  ])
  await page.getByRole('button', { name: '手机 C（C-01）' }).click()
  await expect(page.getByText('模型暂时不可用', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '重试本轮' }).click()
  await expect(page.getByText('已恢复回复')).toBeVisible()
  expect(submitted[0]).toMatchObject({
    comparisonSelection: { comparisonId: 'comparison-1', targetIndex: 2, productId: 3 },
  })
  expect(submitted[1]).toEqual(submitted[0])
  await expect(page.locator('.message.USER')).toHaveCount(1)
  await expect(page.locator('.message.ASSISTANT')).toHaveCount(2)
})

test('transport errors retain their reasons and a new turn retires the old retry', async ({
  page,
}) => {
  let requests = 0
  const ids: string[] = []
  await page.route('**/api/chat/sessions/chat-1/messages/stream', (route) => {
    ids.push(route.request().postDataJSON().clientRequestId)
    requests++
    if (requests === 1) return route.abort('internetdisconnected')
    if (requests === 2)
      return route.fulfill({ status: 429, json: { message: '访客咨询过于频繁', retryable: false } })
    return route.fulfill({ status: 401 })
  })
  await openChat(page)
  const field = page.getByPlaceholder(composer)
  await field.fill('第一轮')
  await field.press('Enter')
  await expect(page.getByText('网络连接中断', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: '重试本轮' }).click()
  await expect(page.getByText('访客咨询过于频繁', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '重试本轮' })).toBeVisible()

  await field.fill('第二轮')
  await field.press('Enter')
  await expect(page.getByText('登录已失效', { exact: false })).toBeVisible()
  await expect(page.getByText('访客咨询过于频繁', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '重试本轮' })).toHaveCount(0)
  expect(ids[0]).toBe(ids[1])
  expect(ids[2]).not.toBe(ids[0])
})

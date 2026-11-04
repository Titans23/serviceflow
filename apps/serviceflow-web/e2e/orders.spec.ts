import { expect, test } from '@playwright/test'

const order = (orderNo: string) => ({
  orderNo,
  status: 'PAID',
  totalAmount: 299,
  createdAt: '2026-11-03T01:00:00Z',
  items: [{ sku: 'EAR-01', productName: '无线耳机', quantity: 1, unitPrice: 299 }],
  payment: { status: 'PAID', refundStatus: 'NONE' },
  refund: null,
})

test('order consultation starts fresh, sends context and supports candidate cards', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'test-customer')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  const first = order('SF202611030001')
  const second = order('SF202611030002')
  let created = 0
  const sessions = [{ publicId: 'old', title: '以前的会话' }]
  const requests: any[] = []
  let confirmations = 0
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: {} } }))
  await page.route('**/api/orders?*', (route) =>
    route.fulfill({ json: { items: [first, second], nextCursor: null } }),
  )
  await page.route('**/api/orders/*', (route) =>
    route.fulfill({ json: route.request().url().endsWith(second.orderNo) ? second : first }),
  )
  await page.route('**/api/chat/sessions', (route) => {
    if (route.request().method() === 'POST') {
      const session = { publicId: 'new-' + ++created, title: '订单咨询' }
      sessions.unshift(session)
      return route.fulfill({ json: session })
    }
    return route.fulfill({ json: sessions })
  })
  await page.route('**/api/chat/sessions/*/messages', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/chat/actions/*/confirm', (route) => {
    confirmations++
    return route.abort()
  })
  await page.route('**/api/chat/sessions/*/messages/stream', (route) => {
    const body = route.request().postDataJSON()
    requests.push(body)
    const candidates = {
      queryId: 'lookup-1',
      items: [first, second],
      hasMore: false,
      cancellation: true,
    }
    const event = body.orderSelection
      ? `event: order\ndata: ${JSON.stringify(second)}\n\nevent: action_required\ndata: {"actionId":"action","actionType":"CANCEL_ORDER","orderNo":"${second.orderNo}","message":"请确认取消第二笔订单"}\n\n`
      : body.message.includes('耳机')
        ? `event: order_selection_required\ndata: ${JSON.stringify(candidates)}\n\n`
        : `event: order\ndata: ${JSON.stringify(first)}\n\n`
    return route.fulfill({
      contentType: 'text/event-stream',
      body: event + 'event: done\ndata: {}\n\n',
    })
  })
  await page.goto('/orders')
  await page.getByRole('link', { name: '咨询这笔订单', exact: true }).first().click()
  await expect(page.locator('.order-context')).toContainText(first.orderNo)
  await expect(page.locator('.session.active')).toHaveText('订单咨询')
  await expect(page.locator('.message')).toHaveCount(0)
  const field = page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')
  await expect(field).toBeFocused()
  await page.reload()
  await expect(field).toBeFocused()
  expect(created).toBe(1)
  await field.fill('它的物流呢')
  await field.press('Enter')
  await expect.poll(() => requests.length).toBe(1)
  expect(requests[0].pageContext).toEqual({ orderNo: first.orderNo })
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  await field.fill('昨天买的耳机不要了')
  await field.press('Enter')
  await expect(page.getByRole('button', { name: '选择这笔订单', exact: true })).toHaveCount(2)
  await expect(page.locator('.order-context')).toHaveCount(0)
  await page.getByRole('button', { name: '选择这笔订单', exact: true }).nth(1).click()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeVisible()
  expect(requests[2].orderSelection).toEqual({ queryId: 'lookup-1', orderNo: second.orderNo })
  await expect(page.locator('.order-context')).toContainText(second.orderNo)
  expect(confirmations).toBe(0)
  await page.getByRole('button', { name: '以前的会话', exact: true }).click()
  await expect(field).toBeFocused()
  await expect(page.locator('.order-context')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  await field.fill('查询订单')
  await field.press('Enter')
  await expect.poll(() => requests.length).toBe(4)
  expect(requests[3].pageContext).toBeNull()
})

test('composer waits for session creation so a new draft cannot be erased by the response', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'test-customer')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  let finishCreation!: () => void
  const creation = new Promise<void>((resolve) => {
    finishCreation = resolve
  })
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: {} } }))
  await page.route('**/api/chat/sessions', async (route) => {
    if (route.request().method() === 'POST') {
      await creation
      return route.fulfill({ json: { publicId: 'created', title: '新会话' } })
    }
    return route.fulfill({ json: [{ publicId: 'existing', title: '旧会话' }] })
  })
  await page.route('**/api/chat/sessions/*/messages', (route) => route.fulfill({ json: [] }))
  const requests: any[] = []
  await page.route('**/api/chat/sessions/*/messages/stream', (route) => {
    requests.push(route.request().postDataJSON())
    return route.fulfill({ contentType: 'text/event-stream', body: 'event: done\ndata: {}\n\n' })
  })
  await page.goto('/chat')
  const field = page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')
  await expect(field).toBeFocused()
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(field).toBeDisabled()
  finishCreation()
  await expect(field).toBeFocused()
  await field.fill('查询订单')
  await field.press('Enter')
  await expect.poll(() => requests.length).toBe(1)
  expect(requests[0].message).toBe('查询订单')
})

test('my orders loads, paginates, filters and opens the owned detail', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'test-customer')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: {} } }))
  const queries: URLSearchParams[] = []
  await page.route('**/api/orders?*', (route) => {
    const params = new URL(route.request().url()).searchParams
    queries.push(params)
    const result = params.has('keyword')
      ? { items: [], nextCursor: null }
      : params.has('cursor')
        ? { items: [order('SF202611030002')], nextCursor: null }
        : { items: [order('SF202611030001')], nextCursor: 100 }
    return route.fulfill({ json: result })
  })
  await page.route('**/api/orders/SF202611030001', (route) =>
    route.fulfill({ json: order('SF202611030001') }),
  )
  await page.goto('/orders')
  await expect(page.getByRole('heading', { name: '我的订单', exact: true })).toBeVisible()
  await expect(page.locator('.order-summary')).toHaveCount(1)
  await page.getByPlaceholder('商品名称、型号或订单号').fill('还未提交的筛选')
  await page.getByRole('button', { name: '加载更多订单' }).click()
  await expect(page.locator('.order-summary')).toHaveCount(2)
  expect(queries[1].get('cursor')).toBe('100')
  expect(queries[1].has('keyword')).toBe(false)
  await page.getByRole('button', { name: '查看详情', exact: true }).first().click()
  await expect(page.getByTestId('order-detail')).toContainText('SF202611030001')
  await expect(page.getByRole('link', { name: '向客服咨询此订单' })).toHaveAttribute(
    'href',
    '/chat?orderNo=SF202611030001',
  )
  await page.getByPlaceholder('商品名称、型号或订单号').fill('不存在的商品')
  await page.getByRole('button', { name: '筛选 / 刷新列表' }).click()
  await expect(page.getByText('没有找到符合条件的订单')).toBeVisible()
  expect(queries.at(-1)?.has('cursor')).toBe(false)
  expect(queries.at(-1)?.get('keyword')).toBe('不存在的商品')
  await page.getByPlaceholder('商品名称、型号或订单号').fill('SF202611030001')
  await page.getByRole('button', { name: '筛选 / 刷新列表' }).click()
  await expect(page.locator('.order-summary')).toHaveCount(1)
  expect(queries.at(-1)?.get('orderNo')).toBe('SF202611030001')
  expect(queries.at(-1)?.has('keyword')).toBe(false)
})

import { expect, test } from '@playwright/test'

const product = {
  id: 1,
  sku: 'PURA80',
  name: '华为 Pura 80',
  brand: '华为',
  model: 'Pura 80',
  category: 'phone',
  specs: { storage: '512 GB', screen_size: '6.6 英寸' },
  listPrice: 6499,
  saleStatus: 'ON_SALE',
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/guest', async (route) => {
    await route.fulfill({ json: { accessToken: 'e2e-guest', role: 'GUEST' } })
  })
  await page.route('**/api/products?*', async (route) => {
    await route.fulfill({ json: { items: [product], page: 0, size: 50, total: 1 } })
  })
  await page.route('**/api/products/1', async (route) => {
    await route.fulfill({ json: product })
  })
})

test('visitor browses a product and opens consultation', async ({ page }) => {
  await page.route('**/api/chat/sessions', (route) =>
    route.fulfill({
      json: route.request().method() === 'POST' ? { publicId: 'new', title: '商品咨询' } : [],
    }),
  )
  await page.route('**/api/chat/sessions/new/messages', (route) => route.fulfill({ json: [] }))
  await page.goto('/products')
  await expect(page.getByText('华为 Pura 80')).toBeVisible()
  await page.getByRole('link', { name: '查看详情' }).click()
  await expect(page.getByText('PURA80')).toBeVisible()
  await page.getByRole('link', { name: '咨询此商品' }).click()
  await expect(page).toHaveURL(/\/chat\?productId=1/)
  await expect(page.locator('.session.active')).toHaveText('商品咨询')
})

test('guest is redirected away from admin routes', async ({ page }) => {
  await page.goto('/admin/knowledge')
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

for (const policyFirst of [false, true]) {
  test(`page product hint follows explicit switches (policy first: ${policyFirst})`, async ({
    page,
  }) => {
    const payloads: any[] = []
    await page.route('**/api/chat/sessions', async (route) => {
      await route.fulfill({
        json: route.request().method() === 'POST' ? { publicId: 'scope-session' } : [],
      })
    })
    await page.route('**/api/chat/sessions/scope-session/messages', async (route) => {
      await route.fulfill({ json: [] })
    })
    await page.route('**/api/chat/sessions/scope-session/messages/stream', async (route) => {
      payloads.push(route.request().postDataJSON())
      const request = route.request().postDataJSON()
      const policy = request.message.includes('保修政策')
      const id = request.pageContext ? 1 : 2
      await route.fulfill({
        contentType: 'text/event-stream',
        body:
          `event: meta\ndata: ${JSON.stringify({ intent: policy ? 'KNOWLEDGE_QUERY' : 'PRODUCT_QUERY', productIds: policy ? [] : [id] })}\n\n` +
          'event: token\ndata: 有来源的回答\n\nevent: done\ndata: {}\n\n',
      })
    })
    await page.goto('/chat?productId=1')
    const messages = [
      ...(policyFirst ? ['保修政策是什么？'] : []),
      '它的屏幕怎样？',
      '改问 SAMSUNG-S24-US-128 的屏幕',
      '它的圆角呢？',
    ]
    for (const message of messages) {
      await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill(message)
      await page.getByRole('button', { name: '发送', exact: true }).click()
      await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
    }
    expect(payloads.map((p) => p.pageContext)).toEqual([
      ...(policyFirst ? [{ productId: 1 }] : []),
      { productId: 1 },
      null,
      null,
    ])
    await page.getByRole('button', { name: '新建会话', exact: true }).click()
    await expect(page.locator('.message')).toHaveCount(0)
    await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill('它的屏幕怎样？')
    await page.getByRole('button', { name: '发送', exact: true }).click()
    await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
    expect(payloads.at(-1).pageContext).toEqual({ productId: 1 })
  })
}

test('product entry creates its own focused session and leaves history unchanged', async ({
  page,
}) => {
  const payloads: any[] = []
  const history = [{ publicId: 'other-session', title: '另一段历史会话' }]
  let created = 0
  await page.route('**/api/chat/sessions', async (route) => {
    if (route.request().method() === 'POST') {
      const session = { publicId: `fresh-${++created}`, title: '商品咨询' }
      history.unshift(session)
      await route.fulfill({ json: session })
    } else await route.fulfill({ json: history })
  })
  await page.route('**/api/chat/sessions/*/messages', async (route) => {
    await route.fulfill({
      json: route.request().url().includes('/other-session/')
        ? [{ role: 'USER', content: '以前的问题' }]
        : [],
    })
  })
  await page.route('**/api/chat/sessions/other-session/messages/stream', async (route) => {
    payloads.push(route.request().postDataJSON())
    await route.fulfill({
      contentType: 'text/event-stream',
      body:
        'event: meta\ndata: {"intent":"PRODUCT_QUERY","productIds":[2]}\n\n' +
        'event: token\ndata: 当前历史会话商品\n\nevent: done\ndata: {}\n\n',
    })
  })
  await page.goto('/products/1')
  await page.getByRole('link', { name: '咨询此商品' }).click()
  await expect(page.locator('.session.active')).toHaveText('商品咨询')
  await expect(page.locator('.message')).toHaveCount(0)
  const field = page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')
  await expect(field).toBeFocused()
  await page.reload()
  await expect(field).toBeFocused()
  expect(created).toBe(1)
  await page.getByText('另一段历史会话', { exact: true }).click()
  await expect(page.locator('.session.active')).toHaveText('另一段历史会话')
  await expect(page.getByText('以前的问题')).toBeVisible()
  await expect(field).toBeFocused()
  await field.fill('它的屏幕呢？')
  await field.press('Shift+Enter')
  await page.keyboard.insertText('字')
  await field.dispatchEvent('keydown', { key: 'Enter', isComposing: true })
  expect(payloads).toHaveLength(0)
  await field.press('Enter')
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  expect(payloads).toHaveLength(1)
  expect(payloads[0].pageContext).toBeNull()
  expect(payloads[0].message).toBe('它的屏幕呢？\n字')
  await page.goto('/products/1')
  await page.getByRole('link', { name: '咨询此商品' }).click()
  await expect(field).toBeFocused()
  expect(created).toBe(2)
  await expect(page.locator('.message')).toHaveCount(0)
})

test('deleting history preserves the active draft and clearing all survives reload', async ({
  page,
}) => {
  let history = ['A', 'B', 'C'].map((id) => ({ publicId: id, title: id }))
  let created = 0
  const deleted: string[] = []
  const payloads: any[] = []
  await page.route('**/api/chat/sessions', async (route) => {
    if (route.request().method() === 'DELETE') {
      deleted.push('all')
      history = []
      await route.fulfill({ status: 204 })
    } else if (route.request().method() === 'POST') {
      const session = { publicId: `new-${++created}`, title: '新会话' }
      history.unshift(session)
      await route.fulfill({ json: session })
    } else await route.fulfill({ json: history })
  })
  await page.route('**/api/chat/sessions/*', async (route) => {
    expect(route.request().method()).toBe('DELETE')
    const id = route.request().url().split('/').pop()!
    deleted.push(id)
    history = history.filter((session) => session.publicId !== id)
    await route.fulfill({ status: 204 })
  })
  await page.route('**/api/chat/sessions/*/messages', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/chat/sessions/*/messages/stream', (route) => {
    payloads.push(route.request().postDataJSON())
    return route.fulfill({ contentType: 'text/event-stream', body: 'event: done\ndata: {}\n\n' })
  })
  await page.goto('/chat')
  const field = page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')
  await expect(field).toBeFocused()
  await field.fill('草稿')
  await page.getByRole('button', { name: '删除会话：B' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '取消' }).click()
  expect(deleted).toEqual([])
  await page.getByRole('button', { name: '删除会话：B' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.session')).toHaveCount(2)
  await expect(field).toHaveValue('草稿')
  await page.getByRole('button', { name: '删除会话：A' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '删除', exact: true }).click()
  await expect(page.locator('.session.active')).toHaveText('C')
  await expect(field).toBeFocused()
  await page.getByRole('button', { name: '清除所有会话' }).click()
  await page.getByRole('dialog').getByRole('button', { name: '全部清除' }).click()
  await expect(page.locator('.session')).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('暂无会话，发送消息即可开始。')).toBeVisible()
  expect(created).toBe(0)
  expect(deleted).toEqual(['B', 'A', 'all'])
  await field.fill('重新开始')
  await field.press('Enter')
  await expect(page.locator('.session.active')).toHaveText('新会话')
  await expect.poll(() => payloads.length).toBe(1)
  expect(created).toBe(1)
  expect(payloads[0].message).toBe('重新开始')
})

test('chat renders safe markdown and keeps the composer visible after a long comparison', async ({
  page,
}) => {
  const fields = Array.from({ length: 16 }, (_, index) => '规格' + index)
  const specs = Object.fromEntries(fields.map((field, index) => [field, '参数' + index]))

  await page.route('**/api/chat/sessions', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({ json: { publicId: 'session-1', title: '商品咨询' } })
      return
    }
    await route.fulfill({ json: [] })
  })
  await page.route('**/api/chat/sessions/session-1/messages', async (route) => {
    await route.fulfill({ json: [] })
  })
  await page.route('**/api/chat/sessions/session-1/messages/stream', async (route) => {
    const comparison = {
      products: [
        { id: 1, name: 'Pura 80', specs },
        { id: 2, name: 'Pura 80 Pro', specs },
        { id: 3, name: 'Pura 80 Ultra', specs },
      ],
      fields,
    }
    const body =
      'event: token\ndata: **100W** 快充 <img src=x onerror=alert(1)>\n\n' +
      'event: product_comparison\ndata: ' +
      JSON.stringify(comparison) +
      '\n\n' +
      'event: done\ndata: {}\n\n'

    await route.fulfill({
      status: 200,
      headers: { 'content-type': 'text/event-stream' },
      body,
    })
  })

  await page.goto('/chat?productId=1')
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill('对比商品')
  await page.getByRole('button', { name: '发送' }).click()

  await expect(page.locator('.markdown strong')).toHaveText('100W')
  await expect(page.locator('.markdown img')).toHaveCount(0)
  await expect(page.locator('.spec-table')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送' })).toBeInViewport()

  const messagePane = page.locator('.messages')
  await expect
    .poll(() => messagePane.evaluate((element) => element.scrollHeight))
    .toBeGreaterThan(await messagePane.evaluate((element) => element.clientHeight))
  await messagePane.evaluate((element) => {
    element.scrollTop = 0
  })
  await messagePane.hover()
  await page.mouse.wheel(0, 600)
  await expect.poll(() => messagePane.evaluate((element) => element.scrollTop)).toBeGreaterThan(0)
})

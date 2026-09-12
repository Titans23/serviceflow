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
  await page.goto('/products')
  await expect(page.getByText('华为 Pura 80')).toBeVisible()
  await page.getByRole('link', { name: '查看详情' }).click()
  await expect(page.getByText('PURA80')).toBeVisible()
  await page.getByRole('link', { name: '咨询此商品' }).click()
  await expect(page).toHaveURL(/\/chat\?productId=1/)
})

test('guest is redirected away from admin routes', async ({ page }) => {
  await page.goto('/admin/knowledge')
  await expect(page).toHaveURL(/\/login\?redirect=/)
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

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
  await page.getByRole('button', { name: '加载更多订单' }).click()
  await expect(page.locator('.order-summary')).toHaveCount(2)
  expect(queries[1].get('cursor')).toBe('100')
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
})

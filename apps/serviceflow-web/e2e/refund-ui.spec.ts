import { expect, test } from '@playwright/test'

test('customer can refresh a failed refund into its completed state', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'ui-fixture')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  let status = 'FAILED'
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: { refundSandboxEnabled: true } } }))
  await page.route('**/api/orders/SF123456789012', (route) => route.fulfill({ json: {
    orderNo: 'SF123456789012', status: 'CANCELLED', totalAmount: 99, items: [],
    payment: { status: 'PAID', refundStatus: status === 'FAILED' ? 'FAILED' : 'REFUNDED' },
    refund: { publicId: 'refund-one', amount: 99, status, attemptNo: status === 'FAILED' ? 1 : 2, failureReason: status === 'FAILED' ? '通道故障' : null, updatedAt: '2026-10-24T00:00:00Z' },
  } }))
  await page.goto('/orders?orderNo=SF123456789012')
  await expect(page.getByText('失败，等待处理', { exact: true })).toBeVisible()
  await expect(page.getByText('处理说明：通道故障')).toBeVisible()
  await expect(page.getByRole('link', { name: '向客服咨询此订单' })).toHaveAttribute('href', '/chat?orderNo=SF123456789012')
  status = 'SUCCEEDED'
  await page.getByRole('button', { name: '查询 / 刷新' }).click()
  await expect(page.getByText('已完成', { exact: true })).toBeVisible()
  await expect(page.getByText('处理说明：通道故障')).toHaveCount(0)
})

test('administrator can simulate failure, retry and then success', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'ui-fixture')
    sessionStorage.setItem('serviceflow_role', 'ADMIN')
  })
  const refund = { publicId: 'refund-one', orderNo: 'SF123456789012', amount: 99, status: 'PROCESSING', attemptNo: 1, failureReason: '', updatedAt: '' }
  const callbacks: { attemptNo: number; outcome: string; amount: number }[] = []
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: { refundSandboxEnabled: true } } }))
  await page.route('**/api/admin/refunds?*', (route) => route.fulfill({ json: [refund] }))
  await page.route('**/api/admin/refunds/refund-one/sandbox-events', (route) => {
    const body = route.request().postDataJSON()
    callbacks.push(body)
    refund.status = body.outcome
    refund.failureReason = body.reason
    return route.fulfill({ json: { refund, replayed: false, disposition: 'APPLIED' } })
  })
  await page.route('**/api/admin/refunds/refund-one/retry', (route) => {
    expect(route.request().postDataJSON()).toEqual({ expectedAttemptNo: 1 })
    refund.status = 'PROCESSING'
    refund.attemptNo = 2
    return route.fulfill({ json: refund })
  })
  await page.goto('/admin/refunds')
  await page.getByRole('button', { name: '模拟失败' }).click()
  await page.locator('.el-message-box').getByRole('textbox').fill('通道故障')
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await page.getByRole('button', { name: '重试退款' }).click()
  await page.getByRole('button', { name: '模拟成功', exact: true }).click()
  await page.locator('.el-message-box').getByRole('button', { name: '确定' }).click()
  await expect(page.getByText('SUCCEEDED', { exact: true })).toBeVisible()
  expect(callbacks.map(({ attemptNo, outcome, amount }) => ({ attemptNo, outcome, amount }))).toEqual([
    { attemptNo: 1, outcome: 'FAILED', amount: 99 }, { attemptNo: 2, outcome: 'SUCCEEDED', amount: 99 },
  ])
})

test('sandbox controls remain hidden when disabled', async ({ page }) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'ui-fixture')
    sessionStorage.setItem('serviceflow_role', 'ADMIN')
  })
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: { refundSandboxEnabled: false } } }))
  await page.route('**/api/admin/refunds?*', (route) => route.fulfill({ json: [{ publicId: 'one', orderNo: 'SF1', amount: 99, status: 'PROCESSING', attemptNo: 1 }] }))
  await page.goto('/admin/refunds')
  await expect(page.getByText('沙箱回调未启用，此页面仅查看退款记录。')).toBeVisible()
  await expect(page.getByRole('button', { name: '模拟成功', exact: true })).toHaveCount(0)
})

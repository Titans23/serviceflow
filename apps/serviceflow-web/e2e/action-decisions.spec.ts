import { expect, test } from '@playwright/test'

test('pending card survives reload and a lost rejection response is recovered without another decision', async ({
  page,
}) => {
  const order = { orderNo: 'SF202611040001', status: 'PAID', totalAmount: 299 }
  const action = {
    actionId: 'durable-action',
    actionType: 'CANCEL_ORDER',
    orderNo: order.orderNo,
    message: '确认取消耳机订单吗？',
  }
  let status = 'PENDING'
  let submissions = 0
  let reads = 0
  await page.addInitScript(() => {
    const payload = btoa(JSON.stringify({ sub: 'user-1', role: 'CUSTOMER', customerId: 1 }))
    sessionStorage.setItem('serviceflow_token', 'header.' + payload + '.signature')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  await page.route('**/api/actuator/info', (route) => route.fulfill({ json: { serviceflow: {} } }))
  await page.route('**/api/chat/sessions', (route) =>
    route.fulfill({ json: [{ publicId: 'session', title: '订单咨询' }] }),
  )
  await page.route('**/api/chat/sessions/session/messages', (route) =>
    route.fulfill({
      json: [
        {
          role: 'ASSISTANT',
          content: '请确认',
          metadataJson: JSON.stringify({ action, order }),
        },
      ],
    }),
  )
  await page.route('**/api/chat/actions/durable-action/result', (route) => {
    reads++
    return route.fulfill({
      json: {
        actionStatus: status,
        operation: {
          requestId: action.actionId,
          resultCode: status,
          resultMessage: '已放弃本次操作',
        },
        order,
      },
    })
  })
  await page.route('**/api/chat/actions/durable-action/confirm', (route) => {
    expect(route.request().postDataJSON().decision).toBe('REJECT')
    submissions++
    status = 'REJECTED'
    return route.abort('failed')
  })
  await page.goto('/chat?sessionId=session')
  await expect(page.getByRole('button', { name: '确认', exact: true })).toBeVisible()
  await page.reload()
  await expect(page.getByRole('button', { name: '放弃本次操作', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '放弃本次操作', exact: true }).click()
  await expect(page.getByTestId('cancellation-result')).toContainText('已放弃本次操作')
  await expect(page.getByTestId('cancellation-result')).toContainText('PAID')
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  expect(
    await page.evaluate(() => sessionStorage.getItem('serviceflow_pending_cancellation_v1')),
  ).toBeNull()
  await page.reload()
  await expect(page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')).toBeFocused()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  expect(submissions).toBe(1)
  const beforeCorruption = reads
  await page.evaluate(() => sessionStorage.setItem('serviceflow_pending_cancellation_v1', 'broken'))
  await page.reload()
  await expect(page.getByTestId('recovery-storage-warning')).toBeVisible()
  await expect(page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')).toBeFocused()
  expect(reads).toBe(beforeCorruption)
  expect(submissions).toBe(1)
})

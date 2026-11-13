import { expect, test } from '@playwright/test'

test('refresh after a read-only turn does not resurrect an earlier cancellation card', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'ui-fixture')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  await page.route('**/api/chat/sessions', (route) =>
    route.fulfill({ json: [{ publicId: 'old', title: '订单咨询' }] }),
  )
  await page.route('**/api/chat/sessions/old/messages', (route) =>
    route.fulfill({
      json: [
        {
          role: 'ASSISTANT',
          content: '请确认取消',
          metadataJson: JSON.stringify({
            action: {
              actionId: 'pending',
              actionType: 'CANCEL_ORDER',
              orderNo: 'SF202611030001',
              message: '确认取消吗',
            },
          }),
        },
        { role: 'USER', content: '不用取消，查一下状态' },
        { role: 'ASSISTANT', content: '订单仍为已付款' },
      ],
    }),
  )
  await page.route('**/api/chat/actions/pending/result', (route) =>
    route.fulfill({ json: { actionStatus: 'PENDING' } }),
  )
  await page.goto('/chat?sessionId=old')
  await expect(page.getByText('订单仍为已付款', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  await page.reload()
  await expect(page.getByText('订单仍为已付款', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
})

test('support explicitly publishes a customer reply without sending the internal note', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'ui-fixture')
    sessionStorage.setItem('serviceflow_role', 'ADMIN')
  })
  const ticket = {
    publicId: 'review-one',
    subject: '退款核验',
    customerName: '顾客',
    status: 'ASSIGNED',
    description: '核验原退款',
    resolutionNote: '内部信息',
    customerMessage: null as string | null,
  }
  await page.route('**/api/admin/ai-audits?*', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/admin/tickets', (route) => route.fulfill({ json: [ticket] }))
  await page.route('**/api/admin/tickets/review-one/customer-message', (route) => {
    expect(route.request().postDataJSON()).toEqual({ message: '已联系渠道核验' })
    ticket.customerMessage = '已联系渠道核验'
    return route.fulfill({ status: 204 })
  })
  await page.goto('/admin/operations')
  await page.getByRole('button', { name: '回复顾客', exact: true }).click()
  const dialog = page.locator('.el-message-box')
  await expect(dialog.getByRole('textbox')).toHaveValue('')
  await dialog.getByRole('textbox').fill('已联系渠道核验')
  await dialog.getByRole('button', { name: '发布', exact: true }).click()
  await expect(page.getByText('已发布顾客回复', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '回复顾客', exact: true }).click()
  await expect(dialog.getByRole('textbox')).toHaveValue('已联系渠道核验')
})

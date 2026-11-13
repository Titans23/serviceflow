import { expect, test } from '@playwright/test'

test('customer follows up a resolved ticket and confirms its later resolution', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'fixture')
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  })
  const ticket = { publicId: 'one', subject: '售后协助', description: '原问题', status: 'RESOLVED' }
  const messages: any[] = [{ publicId: 'reply', authorRole: 'SUPPORT', content: '请检查处理结果' }]
  await page.route('**/api/tickets', (route) => route.fulfill({ json: [ticket] }))
  await page.route('**/api/tickets/one', (route) => route.fulfill({ json: { ticket, messages } }))
  await page.route('**/api/tickets/one/messages', (route) => {
    const body = route.request().postDataJSON()
    expect(body.clientRequestId).toMatch(/^[0-9a-f-]{36}$/)
    ticket.status = 'ASSIGNED'
    const message = {
      publicId: body.clientRequestId,
      authorRole: 'CUSTOMER',
      content: body.content,
    }
    messages.push(message)
    return route.fulfill({ json: message })
  })
  await page.route('**/api/tickets/one/close', (route) => {
    ticket.status = 'CLOSED'
    return route.fulfill({ status: 204 })
  })
  await page.goto('/tickets')
  await page.getByRole('link', { name: '查看与回复' }).click()
  await expect(page.getByText('请检查处理结果', { exact: true })).toBeVisible()
  await page.getByPlaceholder('填写补充说明或回复').fill('问题还存在')
  await page.getByRole('button', { name: '发送回复', exact: true }).click()
  await expect(page.getByText('问题还存在', { exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '确认解决', exact: true })).toHaveCount(0)
  ticket.status = 'RESOLVED'
  await page.getByRole('button', { name: '刷新进度' }).click()
  await page.getByRole('button', { name: '确认解决', exact: true }).click()
  await page
    .locator('.el-message-box')
    .getByRole('button', { name: '确认解决', exact: true })
    .click()
  await expect(page.getByPlaceholder('填写补充说明或回复')).toHaveCount(0)
  await page.reload()
  await expect(page.locator('.ticket-message')).toHaveCount(2)
})

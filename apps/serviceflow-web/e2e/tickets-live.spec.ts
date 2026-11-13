import { expect, test } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'

test.use({ trace: 'off', screenshot: 'off', video: 'off' })
test('customer and support complete a private persistent conversation with reopen and close', async ({
  page,
  browser,
  request,
}) => {
  test.setTimeout(90000)
  test.skip(!process.env.REFUND_DELIVERY_FIXTURES, 'Requires isolated synthetic accounts')
  const path = process.env.REFUND_DELIVERY_FIXTURES!
  const fixture = JSON.parse(readFileSync(path, 'utf8'))
  expect(fixture.synthetic).toBe(true)
  expect((await (await request.get('/api/actuator/info')).json()).serviceflow.aiMode).toBe('demo')
  const login = async (username: string, password: string) =>
    (await (await request.post('/api/auth/login', { data: { username, password } })).json())
      .accessToken
  const customer = await login(fixture.principals.customer, 'Customer123!')
  const admin = await login('admin', 'Admin123!')
  const other = await login(fixture.principals.other, 'Customer123!')
  const headers = { Authorization: 'Bearer ' + customer }
  const session = await (
    await request.post('/api/chat/sessions', { headers, data: { title: 'Ticket acceptance' } })
  ).json()
  const question = '我要投诉服务问题 ' + crypto.randomUUID()
  const chat = await request.post(`/api/chat/sessions/${session.publicId}/messages/stream`, {
    headers,
    data: { message: question, clientRequestId: crypto.randomUUID() },
  })
  expect(chat.ok()).toBe(true)
  const tickets = await (await request.get('/api/tickets', { headers })).json()
  const id = tickets.find((row: any) => row.description === question).publicId
  expect(
    (
      await request.get('/api/tickets/' + id, { headers: { Authorization: 'Bearer ' + other } })
    ).status(),
  ).toBe(404)
  await page.addInitScript((token) => {
    sessionStorage.setItem('serviceflow_token', token)
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  }, customer)
  await page.goto('/tickets')
  await page.locator(`a[href="/tickets/${id}"]`).click()
  await page.getByPlaceholder('填写补充说明或回复').fill('补充说明：请核实售后处理')
  await page.getByRole('button', { name: '发送回复', exact: true }).click()
  await expect(page.getByText('补充说明：请核实售后处理', { exact: true })).toBeVisible()
  const staff = await browser.newPage()
  try {
    await staff.addInitScript((token) => {
      sessionStorage.setItem('serviceflow_token', token)
      sessionStorage.setItem('serviceflow_role', 'ADMIN')
    }, admin)
    await staff.goto(new URL('/admin/tickets/' + id, page.url()).href)
    await staff.getByRole('button', { name: '接手工单', exact: true }).click()
    for (const reply of ['已核实，请检查处理结果', '补充问题也已处理']) {
      await staff.getByRole('button', { name: '刷新进度' }).click()
      await staff.getByPlaceholder('填写补充说明或回复').fill(reply)
      await staff.getByRole('button', { name: '发送回复', exact: true }).click()
      await expect(staff.getByText(reply, { exact: true })).toBeVisible()
      await staff.getByRole('button', { name: '标记解决', exact: true }).click()
      const dialog = staff.locator('.el-message-box')
      await dialog.getByRole('textbox').fill('internal-review-marker')
      await dialog.getByRole('button', { name: '保存', exact: true }).click()
      await page.getByRole('button', { name: '刷新进度' }).click()
      await expect(page.getByRole('button', { name: '确认解决', exact: true })).toBeVisible()
      await expect(page.getByText('internal-review-marker', { exact: true })).toHaveCount(0)
      if (reply.startsWith('已核实')) {
        await page.getByPlaceholder('填写补充说明或回复').fill('还需要补充处理')
        await page.getByRole('button', { name: '发送回复', exact: true }).click()
        await expect(page.getByRole('button', { name: '确认解决', exact: true })).toHaveCount(0)
      }
    }
    await page.getByRole('button', { name: '确认解决', exact: true }).click()
    await page
      .locator('.el-message-box')
      .getByRole('button', { name: '确认解决', exact: true })
      .click()
    await expect(page.getByPlaceholder('填写补充说明或回复')).toHaveCount(0)
    await page.reload()
    await expect(page.locator('.ticket-message')).toHaveCount(4)
    const detail = await (await request.get('/api/tickets/' + id, { headers })).json()
    expect(detail.ticket.status).toBe('CLOSED')
    expect(JSON.stringify(detail)).not.toContain('internal-review-marker')
    writeFileSync(
      join(dirname(path), 'ticket-browser-summary.json'),
      JSON.stringify(
        {
          passed: true,
          realBackend: true,
          messages: detail.messages.length,
          closed: true,
          foreignAccessDenied: true,
          cloudCalls: 0,
        },
        null,
        2,
      ) + '\n',
    )
  } finally {
    await staff.close()
  }
})

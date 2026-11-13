import { expect, test } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'

test.use({ trace: 'off', screenshot: 'off', video: 'off' })

test('customer return, staff review and receipt complete one full refund through the real channel', async ({
  page,
  browser,
  request,
}) => {
  test.setTimeout(90000)
  test.skip(!process.env.REFUND_DELIVERY_FIXTURES, 'Requires isolated synthetic return fixture')
  const fixturePath = process.env.REFUND_DELIVERY_FIXTURES!
  const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))
  expect(fixture.synthetic).toBe(true)
  const no = fixture.cases['browser-return'].orderNo
  expect(no).toMatch(/^SF\d+$/)
  const info = await (await request.get('/api/actuator/info')).json()
  expect(info.serviceflow.aiMode).toBe('demo')
  expect(String(info.serviceflow.refundDispatchEnabled)).toBe('true')
  const customer = await (
    await request.post('/api/auth/login', {
      data: { username: fixture.principals.customer, password: 'Customer123!' },
    })
  ).json()
  const admin = await (
    await request.post('/api/auth/login', { data: { username: 'admin', password: 'Admin123!' } })
  ).json()
  const customerHeaders = { Authorization: 'Bearer ' + customer.accessToken }
  const adminHeaders = { Authorization: 'Bearer ' + admin.accessToken }
  await page.addInitScript((token) => {
    sessionStorage.setItem('serviceflow_token', token)
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  }, customer.accessToken)
  const order = async () =>
    (await request.get('/api/orders/' + no, { headers: customerHeaders })).json()
  await page.goto('/orders?orderNo=' + no)
  await page.getByPlaceholder('请填写退货原因').fill('收货后发现不符合使用需求')
  await page.getByRole('button', { name: '提交退货申请', exact: true }).click()
  await expect(page.getByTestId('return-progress')).toContainText('等待客服审核')
  const initial = await order()
  const id = initial.returnRequest.publicId
  expect(initial.refund).toBeNull()
  const replay = await (
    await request.post('/api/orders/' + no + '/return', {
      headers: customerHeaders,
      data: { reason: '重复提交' },
    })
  ).json()
  expect(replay.publicId).toBe(id)
  for (const step of ['review', 'receive']) {
    expect(
      (
        await request.post(`/api/admin/returns/${id}/${step}`, {
          headers: customerHeaders,
          data: { decision: 'APPROVE', note: '越权' },
        })
      ).status(),
    ).toBe(403)
  }
  expect(
    (
      await request.post(`/api/admin/returns/${id}/receive`, {
        headers: adminHeaders,
        data: { note: '提前收货' },
      })
    ).status(),
  ).toBe(409)
  const adminPage = await browser.newPage()
  try {
    await adminPage.addInitScript((token) => {
      sessionStorage.setItem('serviceflow_token', token)
      sessionStorage.setItem('serviceflow_role', 'ADMIN')
    }, admin.accessToken)
    await adminPage.goto('http://127.0.0.1:28173/admin/returns')
    const card = adminPage.locator('.return-case').filter({ hasText: no })
    await card.getByRole('button', { name: '通过审核', exact: true }).click()
    const dialog = adminPage.locator('.el-message-box')
    await dialog.getByRole('textbox').fill('请交回测试服务台，营业时间内验收')
    await dialog.getByRole('button', { name: '提交审核结果', exact: true }).click()
    await expect(card).toContainText('等待商家收到退货')
    await page.getByRole('button', { name: '查询 / 刷新', exact: true }).click()
    await expect(page.getByTestId('return-progress')).toContainText('请交回测试服务台')
    expect((await order()).refund).toBeNull()
    await request.post('http://127.0.0.1:28090/control/' + id, {
      headers: { 'X-Refund-Token': 'isolated-refund-channel-token' },
      data: { mode: 'DROP_RESPONSE_ONCE' },
    })
    await card.getByRole('button', { name: '确认收货并退款', exact: true }).click()
    await dialog.getByRole('textbox').fill('已收到原商品，配件齐全，验收通过')
    await dialog.getByRole('button', { name: '确认已收货，开始退款', exact: true }).click()
    await expect(card).toContainText('验收通过')
    for (let i = 0; i < 3; i++) {
      const repeated = await request.post(`/api/admin/returns/${id}/receive`, {
        headers: adminHeaders,
        data: { note: '重复收货' },
      })
      expect(repeated.ok()).toBe(true)
      expect((await repeated.json()).publicId).toBe(id)
    }
    await expect
      .poll(async () => (await order()).refund?.status, { timeout: 30000 })
      .toBe('SUCCEEDED')
    await page.getByRole('button', { name: '查询 / 刷新', exact: true }).click()
    await expect(page.getByTestId('return-progress')).toContainText('退货退款已完成')
    await page.getByRole('link', { name: '向客服咨询此订单', exact: true }).click()
    const input = page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策')
    await input.fill('查询退货进度 ' + no)
    await input.press('Enter')
    await expect(page.getByTestId('return-progress')).toContainText(id)
    await expect(page.getByTestId('return-progress')).toContainText('退货退款已完成')
    await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
    const external = await (
      await request.get('http://127.0.0.1:28090/inspect/' + id, {
        headers: { 'X-Refund-Token': 'isolated-refund-channel-token' },
      })
    ).json()
    expect(external.effective_refunds).toBe(1)
    expect(external.accept_count).toBe(1)
    const sql = `SELECT a.status,r.status,r.amount,d.state,(SELECT COUNT(*) FROM refund_request x WHERE x.order_id=o.id) FROM customer_order o JOIN return_request a ON a.order_id=o.id JOIN refund_request r ON r.order_id=o.id JOIN refund_delivery d ON d.refund_id=r.id WHERE o.order_no='${no}';`
    const state = execFileSync(
      'docker',
      [
        'exec',
        '-i',
        'serviceflow-refund-verification-mysql-1',
        'mysql',
        '-userviceflow',
        '-peval-only-password',
        '-N',
        '-B',
        'serviceflow_eval_refund_verification',
      ],
      { input: sql, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] },
    ).trim()
    expect(state).toBe('RECEIVED\tSUCCEEDED\t99.00\tDONE\t1')
    writeFileSync(
      join(dirname(fixturePath), 'return-browser-summary.json'),
      JSON.stringify(
        {
          passed: true,
          realBackend: true,
          databaseState: state,
          effectiveRefunds: external.effective_refunds,
          cloudCalls: 0,
        },
        null,
        2,
      ) + '\n',
    )
  } finally {
    await adminPage.close()
  }
})

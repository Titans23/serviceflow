import { expect, test } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'

test.use({ trace: 'off', screenshot: 'off', video: 'off' })

test('administrator resumes a real manual-review refund and the independent channel refunds once', async ({
  page,
  request,
}) => {
  test.skip(!process.env.REFUND_DELIVERY_FIXTURES, 'Requires isolated automatic-refund fixtures')
  const fixturePath = process.env.REFUND_DELIVERY_FIXTURES!
  const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'))
  expect(fixture.synthetic).toBe(true)
  const entry = fixture.cases['browser-manual']
  expect(entry.orderNo).toMatch(/^SF\d+$/)
  const info = await (await request.get('/api/actuator/info')).json()
  expect(info.serviceflow.aiMode).toBe('demo')
  expect(String(info.serviceflow.refundDispatchEnabled)).toBe('true')
  const login = await request.post('/api/auth/login', {
    data: { username: 'admin', password: 'Admin123!' },
  })
  const { accessToken } = await login.json()
  await page.addInitScript((token: string) => {
    sessionStorage.setItem('serviceflow_token', token)
    sessionStorage.setItem('serviceflow_role', 'ADMIN')
  }, accessToken)
  await page.goto('/admin/refunds')
  const row = page.locator('.el-table__row').filter({ hasText: entry.orderNo })
  await expect(row).toContainText('已转人工工单')
  await row.getByRole('button', { name: '重新核验渠道' }).click()
  await expect
    .poll(
      async () => {
        const response = await request.get('/api/admin/refunds?limit=100', {
          headers: { Authorization: 'Bearer ' + accessToken },
        })
        return (await response.json()).find((r: any) => r.publicId === entry.actionId)?.status
      },
      { timeout: 20000 },
    )
    .toBe('SUCCEEDED')
  await page.getByRole('button', { name: '刷新记录', exact: true }).click()
  await expect(row).toContainText('SUCCEEDED')
  const external = await (
    await request.get('http://127.0.0.1:28090/inspect/' + entry.actionId, {
      headers: { 'X-Refund-Token': 'isolated-refund-channel-token' },
    })
  ).json()
  expect(external.effective_refunds).toBe(1)
  expect(external.accept_count).toBe(1)
  const sql = `SELECT o.status,r.status,d.state,(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),(SELECT COUNT(*) FROM refund_request x WHERE x.order_id=o.id) FROM customer_order o JOIN refund_request r ON r.order_id=o.id JOIN refund_delivery d ON d.refund_id=r.id WHERE o.order_no='${entry.orderNo}';`
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
  expect(state).toBe('CANCELLED\tSUCCEEDED\tDONE\t1\t1')
  writeFileSync(
    join(dirname(fixturePath), 'browser-summary.json'),
    JSON.stringify(
      {
        passed: true,
        realBackend: true,
        realQueue: true,
        independentPersistentChannel: true,
        effectiveRefunds: external.effective_refunds,
        databaseState: state,
        cloudCalls: 0,
      },
      null,
      2,
    ) + '\n',
  )
})

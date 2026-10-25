import { expect, test } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'

test.use({ trace: 'off', screenshot: 'off', video: 'off' })
test.describe.configure({ mode: 'serial' })
test('committed cancellation with lost done is recovered through the real result API', async ({
  page,
  request,
}) => {
  test.skip(!process.env.RECOVERY_FIXTURES, 'Requires opt-in isolated recovery_demo.py fixtures')
  const fixturePath = process.env.RECOVERY_FIXTURES!
  const fixtures = JSON.parse(readFileSync(fixturePath, 'utf8'))
  expect(fixtures.synthetic).toBe(true)
  const orderNo: string = fixtures.cases.unconfirmed.orderNo
  expect(orderNo).toMatch(/^SF\d+$/)
  const info = await (await request.get('/api/actuator/info')).json()
  expect(info.serviceflow.aiMode).toBe('demo')
  expect(String(info.serviceflow.evaluationEnabled)).toBe('true')
  const login = await request.post('/api/auth/login', {
    data: { username: fixtures.principals.customer, password: 'Customer123!' },
  })
  expect(login.status()).toBe(200)
  const { accessToken } = await login.json()
  await page.addInitScript((token: string) => {
    sessionStorage.setItem('serviceflow_token', token)
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  }, accessToken)
  let confirmations = 0
  let reads = 0
  let actionId = ''
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    actionId = route.request().url().split('/').at(-2)!
    const response = await route.fetch() // Real backend commits; only the browser-bound terminal event is dropped.
    const body = await response.text()
    expect(body).toContain('event:done')
    await route.fulfill({
      response,
      body:
        body
          .split(/\r?\n\r?\n/)
          .filter((block) => !block.includes('event:done'))
          .join('\n\n') + '\n\n',
    })
  })
  await page.route('**/api/chat/actions/*/result', async (route) => {
    reads++
    if (reads === 1)
      await route.abort('failed') // Keep uncertainty visible until an explicit second read.
    else await route.continue() // This response is never fabricated.
  })
  const loaded = page.waitForResponse((response) =>
    /\/chat\/sessions\/[^/]+\/messages$/.test(response.url()),
  )
  await page.goto('/chat')
  await loaded
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill(`取消订单 ${orderNo}`)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
  await expect(page.getByText('结果待核验。操作标识：', { exact: false })).toBeVisible()
  expect(confirmations).toBe(1)
  await expect(page.getByRole('button', { name: '确认', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByRole('button', { name: '再次核验结果' }).click()
  await expect(page.getByTestId('cancellation-result')).toContainText(orderNo)
  await expect(page.getByTestId('cancellation-result')).toContainText('已核验操作结果：')
  await expect(page.locator('.message').filter({ hasText: orderNo })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '再次核验结果' })).toHaveCount(0)
  expect(confirmations).toBe(1)
  expect(reads).toBe(2)
  const recovered = await request.get(`/api/chat/actions/${actionId}/result`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  expect(recovered.status()).toBe(200)
  const result = await recovered.json()
  expect(result.operation.resultCode).toBe('CANCELLED')
  expect(result.order.refund.amount).toBe(99)
  const sql = `SELECT o.status,o.version,p.refund_status,(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),(SELECT COUNT(*) FROM refund_request r WHERE r.order_id=o.id) FROM customer_order o JOIN payment p ON p.order_id=o.id WHERE o.order_no='${orderNo}';`
  const state = execFileSync(
    process.env.DOCKER_BIN || 'docker',
    [
      'exec',
      '-i',
      'serviceflow-eval-mysql-1',
      'mysql',
      '-userviceflow',
      '-peval-only-password',
      '-N',
      '-B',
      'serviceflow_eval_v2',
    ],
    { input: sql, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] },
  ).trim()
  expect(state).toBe('CANCELLED\t1\tPROCESSING\t1\t1')
  writeFileSync(
    join(dirname(fixturePath), 'browser-summary.json'),
    JSON.stringify(
      {
        realBackend: true,
        injectedFault: 'terminal done removed after real commit; first read connection fails',
        confirmationRequests: confirmations,
        resultReads: reads,
        databaseState: state,
        automaticSideEffectRetries: 0,
        passed: true,
        refreshPersistence: 'not covered',
      },
      null,
      2,
    ) + '\n',
  )
})

test('unsubmitted confirmation and real 404 retain uncertainty without locking chat or retrying effects', async ({
  page,
  request,
}) => {
  test.skip(!process.env.RECOVERY_FIXTURES, 'Requires opt-in isolated recovery fixtures')
  const fixturePath = process.env.RECOVERY_FIXTURES!
  const fixtures = JSON.parse(readFileSync(fixturePath, 'utf8'))
  expect(fixtures.synthetic).toBe(true)
  const orderNo: string = fixtures.cases['not-sent'].orderNo
  expect(orderNo).toMatch(/^SF\d+$/)
  const info = await (await request.get('/api/actuator/info')).json()
  expect(info.serviceflow.aiMode).toBe('demo')
  expect(String(info.serviceflow.evaluationEnabled)).toBe('true')
  const login = await request.post('/api/auth/login', {
    data: { username: fixtures.principals.customer, password: 'Customer123!' },
  })
  const { accessToken } = await login.json()
  await page.addInitScript((token: string) => {
    sessionStorage.setItem('serviceflow_token', token)
    sessionStorage.setItem('serviceflow_role', 'CUSTOMER')
  }, accessToken)
  let confirmations = 0
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    await route.abort('failed') // Nothing reaches the service; the UI cannot infer that from the transport error.
  })
  const loaded = page.waitForResponse((response) =>
    /\/chat\/sessions\/[^/]+\/messages$/.test(response.url()),
  )
  await page.goto('/chat')
  await loaded
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill(`取消订单 ${orderNo}`)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  const read = page.waitForResponse((response) => response.url().endsWith('/result'))
  await page.getByRole('button', { name: '确认', exact: true }).click()
  expect((await read).status()).toBe(404) // Real authorization-scoped result lookup, not a mocked response.
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  await expect(page.getByRole('link', { name: '查看订单状态', exact: true })).toHaveAttribute(
    'href',
    `/orders?orderNo=${orderNo}`,
  )
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill('你好')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByText('结果待核验。操作标识：', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  expect(confirmations).toBe(1)
  const sql = `SELECT o.status,(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),(SELECT COUNT(*) FROM refund_request r WHERE r.order_id=o.id) FROM customer_order o WHERE o.order_no='${orderNo}';`
  const state = execFileSync(
    process.env.DOCKER_BIN || 'docker',
    [
      'exec',
      '-i',
      'serviceflow-eval-mysql-1',
      'mysql',
      '-userviceflow',
      '-peval-only-password',
      '-N',
      '-B',
      'serviceflow_eval_v2',
    ],
    { input: sql, encoding: 'utf8', stdio: ['pipe', 'pipe', 'pipe'] },
  ).trim()
  expect(state).toBe('PROCESSING\t0\t0')
  writeFileSync(
    join(dirname(fixturePath), 'browser-unknown-summary.json'),
    JSON.stringify(
      {
        realBackend: true,
        confirmationRequests: confirmations,
        resultHttpStatus: 404,
        automaticSideEffectRetries: 0,
        chatRemainsUsable: true,
        databaseState: state,
        passed: true,
      },
      null,
      2,
    ) + '\n',
  )
})

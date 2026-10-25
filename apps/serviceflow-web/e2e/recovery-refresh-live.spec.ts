import { expect, test, type Page, type APIRequestContext } from '@playwright/test'
import { readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'

const key = 'serviceflow_pending_cancellation_v1'
test.use({ trace: 'off', screenshot: 'off', video: 'off' })
test.describe.configure({ mode: 'serial' })
function fixtures() {
  return JSON.parse(readFileSync(process.env.RECOVERY_FIXTURES!, 'utf8'))
}
function evidence(name: string, value: object) {
  writeFileSync(
    join(dirname(process.env.RECOVERY_FIXTURES!), `browser-refresh-${name}.json`),
    JSON.stringify(value, null, 2) + '\n',
  )
}
function state(orderNo: string) {
  expect(orderNo).toMatch(/^SF\d+$/)
  const sql = `SELECT o.status,o.version,(SELECT COUNT(*) FROM order_operation a WHERE a.order_id=o.id),(SELECT COUNT(*) FROM refund_request r WHERE r.order_id=o.id) FROM customer_order o WHERE o.order_no='${orderNo}';`
  return execFileSync(
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
}
async function login(page: Page, username: string) {
  await page.goto('/login')
  await page.locator('.el-form input').nth(0).fill(username)
  await page.locator('.el-form input').nth(1).fill('Customer123!')
  const loaded = page.waitForResponse((r) => /\/chat\/sessions\/[^/]+\/messages$/.test(r.url()))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await loaded
}
async function setup(page: Page, request: APIRequestContext) {
  test.skip(!process.env.RECOVERY_FIXTURES, 'Requires isolated real recovery fixtures')
  const info = await (await request.get('/api/actuator/info')).json()
  expect(info.serviceflow.aiMode).toBe('demo')
  expect(String(info.serviceflow.evaluationEnabled)).toBe('true')
  expect(fixtures().synthetic).toBe(true)
  await login(page, fixtures().principals.customer)
}
async function askToCancel(page: Page, orderNo: string) {
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill(`取消订单 ${orderNo}`)
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await page.getByRole('button', { name: '确认', exact: true }).click()
}

test('committed cancellation survives reload with one confirmation and clears its marker', async ({
  page,
  request,
}) => {
  await setup(page, request)
  const order = fixtures().cases['refresh-committed'].orderNo
  let markerSavedBeforeConfirmation = false
  let confirmations = 0,
    reads = 0
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    markerSavedBeforeConfirmation = await page.evaluate(
      (k) => sessionStorage.getItem(k) !== null,
      key,
    )
    const response = await route.fetch()
    const body = await response.text()
    expect(body).toContain('event:done')
    await route.fulfill({
      response,
      body:
        body
          .split(/\r?\n\r?\n/)
          .filter((b) => !b.includes('event:done'))
          .join('\n\n') + '\n\n',
    })
  })
  await page.route('**/api/chat/actions/*/result', async (route) => {
    reads++
    if (reads === 1) await route.abort('failed')
    else await route.continue()
  })
  await askToCancel(page, order)
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  const saved = await page.evaluate((k) => JSON.parse(sessionStorage.getItem(k)!), key)
  expect(Object.keys(saved).sort()).toEqual([
    'actionId',
    'orderNo',
    'owner',
    'sessionId',
    'version',
  ])
  const recovered = page.waitForResponse((r) => r.url().endsWith('/result'))
  await page.reload()
  expect((await recovered).status()).toBe(200)
  await expect(page.getByTestId('cancellation-result')).toContainText(order)
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBeNull()
  expect(confirmations).toBe(1)
  expect(markerSavedBeforeConfirmation).toBe(true)
  expect(reads).toBe(2)
  expect(state(order)).toBe('CANCELLED\t1\t1\t1')
  await page.goto('/products')
  await page.goto('/chat')
  await expect(page.getByTestId('cancellation-result')).toHaveCount(0)
  expect(confirmations).toBe(1)
  evidence('committed', {
    passed: true,
    realBackend: true,
    confirmations,
    reads,
    markerCleared: true,
    markerSavedBeforeConfirmation,
    databaseState: state(order),
  })
})

test('404 stays unknown across reload and navigation without blocking consultation', async ({
  page,
  request,
}) => {
  await setup(page, request)
  const order = fixtures().cases['refresh-unknown'].orderNo
  let confirmations = 0,
    reads = 0
  page.on('response', (r) => {
    if (r.url().endsWith('/result')) {
      expect(r.status()).toBe(404)
      reads++
    }
  })
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    await route.abort('failed')
  })
  await askToCancel(page, order)
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  const saved = await page.evaluate((k) => sessionStorage.getItem(k), key)
  await page.reload()
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  await page.goto('/products')
  await page.goto('/chat')
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBe(saved)
  await page.getByRole('button', { name: '新建会话', exact: true }).click()
  await expect(page.locator('.message')).toHaveCount(0)
  await page.getByPlaceholder('咨询商品、订单、物流、退款或售后政策').fill('你好')
  await page.getByRole('button', { name: '发送', exact: true }).click()
  await expect(page.getByRole('button', { name: '发送', exact: true })).toBeEnabled()
  expect(confirmations).toBe(1)
  expect(reads).toBe(3)
  expect(state(order)).toBe('PROCESSING\t0\t0\t0')
  evidence('unknown', {
    passed: true,
    realBackend: true,
    confirmations,
    reads,
    markerRetained: true,
    chatUsable: true,
    databaseState: state(order),
  })
})

test('account change and logout never display or query the previous owner marker', async ({
  page,
  request,
}) => {
  await setup(page, request)
  const order = fixtures().cases['refresh-account'].orderNo
  let confirmations = 0,
    reads = 0
  page.on('request', (r) => {
    if (r.url().endsWith('/result')) reads++
  })
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    await route.abort('failed')
  })
  await askToCancel(page, order)
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  const saved = await page.evaluate((k) => sessionStorage.getItem(k)!, key)
  await login(page, fixtures().principals.other)
  await expect(page.getByRole('button', { name: '再次核验结果' })).toHaveCount(0)
  await expect(page.getByTestId('cancellation-result')).toHaveCount(0)
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBeNull()
  // Even a stale marker left behind by failed cleanup cannot cross the identity boundary.
  await page.evaluate(({ key, saved }) => sessionStorage.setItem(key, saved), { key, saved })
  await page.reload()
  await expect(page.getByRole('button', { name: '新建会话', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '再次核验结果' })).toHaveCount(0)
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBeNull()
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await page.waitForURL('**/products')
  await page.goto('/chat')
  await expect(page.getByRole('button', { name: '再次核验结果' })).toHaveCount(0)
  await expect(page.getByTestId('cancellation-result')).toHaveCount(0)
  expect(reads).toBe(1)
  expect(confirmations).toBe(1)
  expect(state(order)).toBe('PROCESSING\t0\t0\t0')
  evidence('identity', {
    passed: true,
    realBackend: true,
    accountChange: true,
    logout: true,
    staleMarkerSuppressed: true,
    confirmations,
    reads,
    databaseState: state(order),
  })
})

test('corrupt or unreadable marker is explicit and never treated as authorization', async ({
  page,
  request,
}) => {
  await setup(page, request)
  let sideEffects = 0,
    reads = 0
  page.on('request', (r) => {
    if (r.url().endsWith('/confirm')) sideEffects++
    if (r.url().endsWith('/result')) reads++
  })
  await page.evaluate((k) => sessionStorage.setItem(k, '{broken'), key)
  await page.reload()
  await expect(page.getByTestId('recovery-storage-warning')).toContainText('损坏')
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBeNull()
  await page.addInitScript((k) => {
    const get = Storage.prototype.getItem
    Storage.prototype.getItem = function (name) {
      if (name === k) throw new Error('storage unavailable')
      return get.call(this, name)
    }
  }, key)
  await page.reload()
  await expect(page.getByTestId('recovery-storage-warning')).toContainText('无法读取')
  await expect(page.getByRole('button', { name: '再次核验结果' })).toHaveCount(0)
  expect(sideEffects).toBe(0)
  expect(reads).toBe(0)
  evidence('invalid-storage', {
    passed: true,
    corruptWarning: true,
    unavailableWarning: true,
    sideEffects,
    reads,
  })
})

test('failed marker write warns while keeping current-page read-only recovery', async ({
  page,
  request,
}) => {
  await setup(page, request)
  const order = fixtures().cases['refresh-storage'].orderNo
  await page.evaluate((k) => {
    const set = Storage.prototype.setItem
    Storage.prototype.setItem = function (name, value) {
      if (name === k) throw new Error('quota')
      return set.call(this, name, value)
    }
  }, key)
  let confirmations = 0,
    reads = 0
  await page.route('**/api/chat/actions/*/confirm', async (route) => {
    confirmations++
    await route.abort('failed')
  })
  page.on('request', (r) => {
    if (r.url().endsWith('/result')) reads++
  })
  await askToCancel(page, order)
  await expect(page.getByTestId('recovery-storage-warning')).toContainText('无法保存')
  await expect(page.getByText('当前存储异常', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: '再次核验结果' })).toBeVisible()
  expect(await page.evaluate((k) => sessionStorage.getItem(k), key)).toBeNull()
  expect(confirmations).toBe(1)
  expect(reads).toBe(1)
  expect(state(order)).toBe('PROCESSING\t0\t0\t0')
  evidence('write-failure', {
    passed: true,
    realBackend: true,
    persistenceClaimed: false,
    currentPageRecoveryAvailable: true,
    confirmations,
    reads,
    databaseState: state(order),
  })
})

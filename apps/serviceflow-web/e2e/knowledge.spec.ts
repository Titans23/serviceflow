import { expect, test } from '@playwright/test'

test('support updates an existing document and retries a failed index without losing the document', async ({
  page,
}) => {
  await page.addInitScript(() => {
    sessionStorage.setItem('serviceflow_token', 'fixture')
    sessionStorage.setItem('serviceflow_role', 'ADMIN')
  })
  const row = {
    publicId: 'document-one',
    title: '退货政策',
    documentType: 'POLICY',
    latestVersion: 1,
    latestVersionId: 1,
    latestStatus: 'READY',
  }
  let updates = 0,
    retries = 0
  await page.route('**/api/admin/knowledge-documents', (route) => route.fulfill({ json: [row] }))
  await page.route('**/api/admin/knowledge-documents/document-one/versions', (route) => {
    updates++
    expect(route.request().postData()).toContain('updated policy')
    row.latestVersion = 2
    row.latestVersionId = 2
    row.latestStatus = 'FAILED'
    return route.fulfill({ json: { id: 2, status: 'PENDING' } })
  })
  await page.route('**/api/admin/knowledge-document-versions/2/retry', (route) => {
    retries++
    row.latestVersion = 3
    row.latestVersionId = 3
    row.latestStatus = 'PENDING'
    return route.fulfill({ status: 204 })
  })
  await page.goto('/admin/knowledge')
  await page.getByRole('button', { name: '更新资料' }).click()
  await page
    .locator('input[type=file]')
    .setInputFiles({
      name: 'policy.md',
      mimeType: 'text/markdown',
      buffer: Buffer.from('# updated policy'),
    })
  await page.getByRole('button', { name: '上传新版本', exact: true }).click()
  await expect(page.getByRole('button', { name: '重建索引', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '重建索引', exact: true }).click()
  await expect(
    page.getByText('已创建新的索引版本，原生效资料继续服务', { exact: true }),
  ).toBeVisible()
  expect(updates).toBe(1)
  expect(retries).toBe(1)
})

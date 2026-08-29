import { expect, test } from '@playwright/test'

const product = {
  id: 1,
  sku: 'PURA80',
  name: '华为 Pura 80',
  brand: '华为',
  model: 'Pura 80',
  category: 'phone',
  specs: { storage: '512 GB', screen_size: '6.6 英寸' },
  listPrice: 6499,
  saleStatus: 'ON_SALE',
}

test.beforeEach(async ({ page }) => {
  await page.route('**/api/auth/guest', async (route) => {
    await route.fulfill({ json: { accessToken: 'e2e-guest', role: 'GUEST' } })
  })
  await page.route('**/api/products?*', async (route) => {
    await route.fulfill({ json: { items: [product], page: 0, size: 50, total: 1 } })
  })
  await page.route('**/api/products/1', async (route) => {
    await route.fulfill({ json: product })
  })
})

test('visitor browses a product and opens consultation', async ({ page }) => {
  await page.goto('/products')
  await expect(page.getByText('华为 Pura 80')).toBeVisible()
  await page.getByRole('link', { name: '查看详情' }).click()
  await expect(page.getByText('PURA80')).toBeVisible()
  await page.getByRole('link', { name: '咨询此商品' }).click()
  await expect(page).toHaveURL(/\/chat\?productId=1/)
})

test('guest is redirected away from admin routes', async ({ page }) => {
  await page.goto('/admin/knowledge')
  await expect(page).toHaveURL(/\/login\?redirect=/)
})

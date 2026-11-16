import assert from 'node:assert/strict'
import { existsSync, mkdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { dirname, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const root = resolve(dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(resolve(root, 'apps/serviceflow-web/package.json'))
const { chromium } = require('@playwright/test')
const chrome =
  process.env.CHROME_PATH ||
  (process.platform === 'win32'
    ? [
        'C:/Program Files/Google/Chrome/Application/chrome.exe',
        'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
      ].find(existsSync)
    : undefined)
const browser = await chromium.launch({
  headless: true,
  ...(chrome ? { executablePath: chrome } : {}),
})
try {
  const context = await browser.newContext({
    offline: true,
    viewport: { width: 1440, height: 1000 },
  })
  const page = await context.newPage()
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto(pathToFileURL(resolve(root, 'docs/architecture-guide.html')).href)
  const data = await page.locator('#wiki-data').textContent().then(JSON.parse)
  for (const chapter of data) {
    await page.locator(`#sidebar a[href="#article/${chapter.id}"]`).click()
    await page.waitForFunction(
      (title) => document.querySelector('#article h1')?.textContent === title,
      chapter.title,
    )
    assert.equal(await page.locator('#article h1').count(), 1)
    assert.equal(await page.locator('#sidebar [aria-current="page"]').count(), 1)
    assert.equal(await page.locator('#toc a').count(), chapter.headings.length)
    assert.equal(
      await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
      false,
      chapter.id,
    )
  }
  await page.locator('#sidebar a[href="#article/rag"]').click()
  await page.locator('#toc a').nth(1).click()
  assert.match(page.url(), /#article\/rag\//)
  await page.waitForFunction(() => {
    const id = decodeURIComponent(location.hash.split('/')[2])
    const y = document.getElementById(id)?.getBoundingClientRect().top
    return y >= 0 && y < 200
  })
  await page.locator('#search').fill('Recall')
  assert.ok((await page.locator('#result-list a').count()) >= 1)
  await page.locator('#search').press('Enter')
  await page.waitForFunction(() => location.hash === '#article/evaluation')
  await page.locator('#search').fill('租约')
  assert.ok((await page.locator('#result-list a').count()) >= 1)
  await page.locator('#search').press('Escape')
  assert.equal(await page.locator('#results').isVisible(), false)
  await page.locator('#search').fill('zzzz-no-result')
  assert.equal(await page.locator('#result-list a').count(), 0)
  await page.locator('#search').press('Escape')
  const theme = await page.locator('html').getAttribute('data-theme')
  await page.locator('#theme').click()
  assert.notEqual(await page.locator('html').getAttribute('data-theme'), theme)
  await page.reload()
  assert.notEqual(await page.locator('html').getAttribute('data-theme'), theme)
  // Optional local review artifacts; no snapshots or model/service dependencies.
  const shots = process.env.WIKI_SCREENSHOT_DIR
  if (shots) {
    mkdirSync(shots, { recursive: true })
    await page.screenshot({ path: resolve(shots, 'wiki-desktop.png') })
  }
  await page.setViewportSize({ width: 390, height: 844 })
  assert.equal(await page.locator('#sidebar').isVisible(), false)
  await page.locator('#menu').click()
  assert.equal(await page.locator('#sidebar').isVisible(), true)
  await page.locator('#sidebar a[href="#article/overview"]').click()
  await page.waitForFunction(
    () => document.querySelector('#article h1')?.textContent === 'ServiceFlow 架构导读',
  )
  assert.equal(await page.locator('#sidebar').isVisible(), false)
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false)
  if (shots) await page.screenshot({ path: resolve(shots, 'wiki-mobile.png') })
  await page.evaluate(() => {
    location.hash = '#article/missing'
  })
  await page.waitForFunction(() => location.hash === '#article/overview')
  assert.deepEqual(errors, [])
  console.log(
    `Wiki UI passed offline: ${data.length} chapters, deep links, search, theme, mobile navigation.`,
  )
} finally {
  await browser.close()
}

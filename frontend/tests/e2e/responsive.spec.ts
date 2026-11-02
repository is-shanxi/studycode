import { expect, test } from '@playwright/test'

import type { SessionView } from './types'
import { apiJson, apiLogin } from './helpers'

/**
 * Engineering baselines only: the product has not confirmed final supported
 * widths, so these tests guard against horizontal overflow and lost navigation
 * at 360 px, 768 px and 1280 px without claiming product acceptance.
 */
const VIEWPORTS = [
  { label: '360px', width: 360, height: 800 },
  { label: '768px', width: 768, height: 900 },
  { label: '1280px', width: 1280, height: 900 },
]

const APP_PAGES = ['/app', '/app/library', '/app/selftest', '/app/notifications']

for (const viewport of VIEWPORTS) {
  test(`app shell and pages remain usable at ${viewport.label}`, async ({ page }) => {
    await apiLogin(page.request, 'student01', 'Student@12345')
    await page.setViewportSize({ width: viewport.width, height: viewport.height })

    for (const path of APP_PAGES) {
      await page.goto(path)
      await expect(page.locator('.app-header')).toBeVisible()
      await expect(page.locator('.app-nav')).toBeVisible()
      await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible()

      const overflow = await page.evaluate(
        () => document.documentElement.scrollWidth - window.innerWidth,
      )
      expect(
        overflow,
        `${path} must not overflow horizontally at ${viewport.label}`,
      ).toBeLessThanOrEqual(1)
    }
  })
}

test('practice session layout remains usable at 360px', async ({ page }) => {
  await apiLogin(page.request, 'student01', 'Student@12345')
  const session = await apiJson<SessionView>(
    page.request,
    'POST',
    '/api/v1/practice-sessions',
    {
      mode: 'NORMAL',
      filters: { historyDays: 90 },
      requestedCount: 10,
    },
    { 'Idempotency-Key': `responsive-${Date.now()}` },
  )

  await page.setViewportSize({ width: 360, height: 800 })
  await page.goto(`/app/practice/${session.sessionId}`)
  await expect(page.getByRole('heading', { name: '普通练习' })).toBeVisible()
  await expect(page.locator('.item-card').first()).toBeVisible()

  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - window.innerWidth,
  )
  expect(overflow, 'practice session must not overflow horizontally at 360px').toBeLessThanOrEqual(
    1,
  )
})

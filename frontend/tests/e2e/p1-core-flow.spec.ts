import { expect, test } from '@playwright/test'

import type { SessionView } from './types'
import { apiJson, apiLogin } from './helpers'

test('a new user can register and land on the practice home', async ({ page }) => {
  const username = `e2e${Date.now()}`
  await page.goto('/app/register')
  await page.getByPlaceholder('3-64 个字符').fill(username)
  await page.getByPlaceholder('至少 8 个字符').fill('Password@123')
  await page.getByPlaceholder('再次输入密码').fill('Password@123')
  await page.getByRole('button', { name: '注册并登录' }).click()

  await expect(page).toHaveURL(/\/app$/)
  await expect(page.getByRole('heading', { level: 1 })).toContainText('欢迎回来')
})

test('a student completes a practice session and all fill self-assessments', async ({ page }) => {
  test.setTimeout(120_000)
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
    { 'Idempotency-Key': `e2e-${Date.now()}` },
  )

  await page.goto(`/app/practice/${session.sessionId}`)
  await expect(page.getByRole('heading', { name: '普通练习' })).toBeVisible()

  const cards = page.locator('.item-card')
  const count = await cards.count()
  expect(count).toBeGreaterThan(0)

  for (let index = 0; index < count; index++) {
    const card = cards.nth(index)
    const radios = card.locator('.el-radio')
    if ((await radios.count()) > 0) {
      await radios.first().click()
    } else {
      await card.getByPlaceholder('输入你的答案').fill('测试答案')
      await card.getByRole('button', { name: '保存' }).click()
    }
    await expect(card.getByText('已作答')).toBeVisible({ timeout: 15_000 })
  }

  await page.getByRole('button', { name: '提交练习' }).click()
  await expect(page).toHaveURL(/\/app\/practice\/.+\/result$/)
  await expect(page.getByRole('heading', { name: '本次练习结果' })).toBeVisible()

  const resultUrl = page.url()
  for (let guard = 0; guard < 12; guard++) {
    await expect(page.locator('.summary-grid')).toBeVisible({ timeout: 15_000 })
    const assessLinks = page.getByRole('link', { name: /去自评/ })
    if ((await assessLinks.count()) === 0) {
      break
    }
    await assessLinks.first().click()
    await page.getByRole('button', { name: '自评成功' }).click()
    const completedLink = page.getByRole('link', { name: '查看完整结果' })
    if ((await completedLink.count()) > 0) {
      await completedLink.click()
    } else {
      await page.goto(resultUrl)
    }
  }

  await expect(page.locator('.summary-grid')).toContainText('已完成', { timeout: 15_000 })
})

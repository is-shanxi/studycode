import { expect, test } from '@playwright/test'

test('renders the public login shell', async ({ page }) => {
  await page.goto('/app/login')

  await expect(page.getByRole('heading', { name: '登录' })).toBeVisible()
  await expect(page).toHaveTitle('考证刷题平台')
})

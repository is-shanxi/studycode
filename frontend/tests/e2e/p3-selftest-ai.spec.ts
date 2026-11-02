import { expect, test, type APIRequestContext } from '@playwright/test'

import type { ResultView, SessionView } from './types'
import { apiJson, apiLogin, csrfToken } from './helpers'

const DEMO_SUBJECT_ID = '11000000-0000-0000-0000-000000000001'

async function answerAllItems(request: APIRequestContext, session: SessionView): Promise<void> {
  for (const item of session.items) {
    const answer =
      item.question.type === 'SINGLE_CHOICE'
        ? { selectedOptionId: item.question.options[0]?.id }
        : { text: '测试答案' }
    await apiJson(
      request,
      'PUT',
      `/api/v1/practice-sessions/${session.sessionId}/items/${item.itemId}/answer`,
      {
        answerRevision: 0,
        answer,
      },
    )
  }
}

async function submitAndAssess(
  request: APIRequestContext,
  session: SessionView,
): Promise<ResultView> {
  const stamp = Date.now()
  const submitted = await apiJson<ResultView>(
    request,
    'POST',
    `/api/v1/practice-sessions/${session.sessionId}/submit`,
    undefined,
    { 'Idempotency-Key': `e2e-p3-submit-${stamp}` },
  )
  for (const [index, item] of submitted.items.entries()) {
    if (item.type === 'SINGLE_BLANK' && !item.selfAssessmentResult) {
      await apiJson(
        request,
        'POST',
        `/api/v1/practice-sessions/${session.sessionId}/items/${item.itemId}/self-assessment`,
        { result: 'PASS' },
        { 'Idempotency-Key': `e2e-p3-assess-${stamp}-${index}` },
      )
    }
  }
  return apiJson<ResultView>(
    request,
    'GET',
    `/api/v1/practice-sessions/${session.sessionId}/result`,
  )
}

test('a student creates a 6:4 self-test and uses AI tutoring after completion', async ({
  page,
}) => {
  test.setTimeout(180_000)
  const username = `p3${Date.now()}`
  const password = 'Password@123'
  await page.goto('/app/register')
  await page.getByPlaceholder('3-64 个字符').fill(username)
  await page.getByPlaceholder('至少 8 个字符').fill(password)
  await page.getByPlaceholder('再次输入密码').fill(password)
  await page.getByRole('button', { name: '注册并登录' }).click()
  await expect(page).toHaveURL(/\/app$/)

  await page.goto('/app/selftest')
  await expect(page.getByRole('heading', { name: '按薄弱范围组一份自测' })).toBeVisible()
  await page.getByRole('button', { name: '开始自测' }).click()

  await expect(page).toHaveURL(/\/app\/practice\/.+/)
  await expect(page.getByRole('heading', { name: '个性化自测' })).toBeVisible()
  await expect(page.locator('.lead')).toContainText('原题 6 / AI 变式 4')

  const sessionId = page.url().split('/').at(-1) ?? ''
  const session = await apiJson<SessionView>(
    page.request,
    'GET',
    `/api/v1/practice-sessions/${sessionId}`,
  )
  await answerAllItems(page.request, session)
  const result = await submitAndAssess(page.request, session)
  expect(result.allowedActions).toContain('AI_TUTOR')
  expect(result.composition?.aiVariant).toBe(4)

  await page.goto(`/app/practice/${sessionId}/result`)
  await expect(page.getByRole('heading', { name: '本题讲解' })).toBeVisible()
  await page.getByRole('link', { name: /第 1 题/ }).click()

  await expect(page).toHaveURL(/\/app\/assistant\?/)
  await expect(page.getByRole('heading', { name: '本题讲解' })).toBeVisible()
  await expect(page.getByText('AI 额度')).toBeVisible()

  await page
    .getByPlaceholder('输入问题，Enter 发送，Shift+Enter 换行')
    .fill('请解释这道题的解题思路')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText(/\[mock\] 已收到你的问题/)).toBeVisible({ timeout: 60_000 })
})

test('the server rejects question AI before the session is submitted', async ({ page }) => {
  await apiLogin(page.request, 'student01', 'Student@12345')
  const session = await apiJson<SessionView>(
    page.request,
    'POST',
    '/api/v1/practice-sessions',
    {
      mode: 'NORMAL',
      filters: {
        subjectId: DEMO_SUBJECT_ID,
        chapterIds: ['12000000-0000-0000-0000-000000000002'],
        historyDays: 90,
      },
      requestedCount: 1,
    },
    { 'Idempotency-Key': `e2e-p3-gate-${Date.now()}` },
  )
  const itemId = session.items[0]?.itemId

  const response = await page.request.post('/api/v1/ai/conversations', {
    headers: { 'X-XSRF-TOKEN': await csrfToken(page.request) },
    data: { scope: 'QUESTION', sessionItemId: itemId },
  })

  expect(response.status()).toBe(409)
  expect((await response.json()).code).toBe('STATE_CONFLICT')

  await apiJson(page.request, 'POST', `/api/v1/practice-sessions/${session.sessionId}/abandon`)
})

test('the administrator sees seeded AI generation candidates', async ({ page }) => {
  await apiLogin(page.request, 'admin', 'Admin@12345')
  await page.goto('/admin/ai')
  await expect(page.getByRole('heading', { name: '批量生成与审核' })).toBeVisible()

  const pendingRow = page.locator('.el-table__row', { hasText: 'PENDING_REVIEW' }).first()
  await expect(pendingRow).toBeVisible()
  await pendingRow.getByRole('button', { name: '查看候选' }).click()

  await expect(page).toHaveURL(/\/admin\/ai\/candidates\/.+/)
  await expect(page.getByText('待审核变式：栈结构最适合描述下列哪种行为？')).toBeVisible()
  await expect(page.getByRole('button', { name: '批准并发布' }).first()).toBeVisible()
  await expect(page.getByText('审核 PENDING').first()).toBeVisible()
})

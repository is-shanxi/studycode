import { expect, test, request as playwrightRequest } from '@playwright/test'

import type { AdminQuestionDetail, CatalogTree, SessionView } from './types'
import { apiJson, apiLogin } from './helpers'

interface QuestionSearchPage {
  total: number
  items: Array<{ questionId: string; stem: string; status: string }>
}

interface AdminTag {
  id: string
  name: string
}

test('a student can search the library, favorite a question and remove it', async ({ page }) => {
  await apiLogin(page.request, 'student01', 'Student@12345')
  await page.goto('/app/library')
  await expect(page.getByRole('heading', { name: '筛选与练习' })).toBeVisible()

  await page.getByPlaceholder('关键词').fill('TCP')
  await page.getByRole('button', { name: '搜索' }).click()

  const demoStem = '在 TCP/IP 协议中，可靠传输由哪一层负责？'
  const card = page.locator('.question-card', { hasText: demoStem })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: '收藏', exact: true }).click()
  await expect(card.getByRole('button', { name: '取消收藏' })).toBeVisible()

  await page.goto('/app/favorites')
  await expect(page.getByRole('heading', { name: '我的收藏' })).toBeVisible()
  const favorite = page.locator('.favorite-card', { hasText: demoStem })
  await expect(favorite).toBeVisible()
  await favorite.getByRole('button', { name: '取消收藏' }).click()
  await expect(page.getByText('还没有收藏题目。')).toBeVisible()
})

test('a student can review records and remove a wrong question', async ({ page }) => {
  test.setTimeout(120_000)
  const baseURL = 'http://localhost:5173'
  const admin = await playwrightRequest.newContext({ baseURL })

  try {
    await apiLogin(admin, 'admin', 'Admin@12345')
    const tree = await apiJson<CatalogTree>(admin, 'GET', '/api/v1/catalog/tree')
    const subject = tree.certificates[0]?.subjects[0]
    expect(subject).toBeTruthy()
    const chapterId = subject!.chapters[0]!.id
    const stem = `e2e wrong question ${Date.now()}`

    const created = await apiJson<AdminQuestionDetail>(admin, 'POST', '/api/v1/admin/questions', {
      subjectId: subject!.id,
      chapterId,
      type: 'SINGLE_CHOICE',
      stem,
      explanation: `Explanation for ${stem}`,
      difficulty: 'MEDIUM',
      sourceText: 'E2E core features',
      options: [
        { key: 'A', content: 'Wrong option', correct: false },
        { key: 'B', content: 'Correct option', correct: true },
      ],
      aliases: [],
      tagIds: [],
    })
    const versionId = created.versions[0]!.versionId
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${versionId}/submit-review`)
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${versionId}/approve`, {
      publishNow: true,
    })

    await apiLogin(page.request, 'student01', 'Student@12345')
    const session = await apiJson<SessionView>(
      page.request,
      'POST',
      '/api/v1/practice-sessions',
      {
        mode: 'NORMAL',
        filters: { subjectId: subject!.id },
        requestedCount: 100,
      },
      { 'Idempotency-Key': `e2e-core-wrong-${Date.now()}` },
    )
    const target = session.items.find((item) => item.question.stem === stem)
    expect(target).toBeTruthy()

    for (const item of session.items) {
      const answer =
        item.question.type === 'SINGLE_CHOICE'
          ? {
              selectedOptionId:
                item.itemId === target!.itemId
                  ? item.question.options.find((option) => option.key === 'A')!.id
                  : item.question.options[0]!.id,
            }
          : { text: 'e2e answer' }
      await apiJson(
        page.request,
        'PUT',
        `/api/v1/practice-sessions/${session.sessionId}/items/${item.itemId}/answer`,
        { answerRevision: 0, answer },
      )
    }
    await apiJson(
      page.request,
      'POST',
      `/api/v1/practice-sessions/${session.sessionId}/submit`,
      undefined,
      { 'Idempotency-Key': `e2e-core-submit-${Date.now()}` },
    )

    await page.goto('/app/wrong-questions')
    await expect(page.getByRole('heading', { name: '待复习错题' })).toBeVisible()
    const wrongCard = page.locator('.wrong-card', { hasText: stem })
    await expect(wrongCard).toBeVisible()
    await wrongCard.getByRole('button', { name: '移出错题集' }).click()
    await expect(wrongCard).toHaveCount(0)

    await page.goto('/app/records')
    await expect(page.getByRole('heading', { name: '刷题记录' })).toBeVisible()
    await expect(page.locator('.record-card').first()).toBeVisible()
    await page.goto(`/app/records/${session.sessionId}`)
    await expect(page.getByRole('heading', { name: '记录详情' })).toBeVisible()
    await expect(page.getByText(stem, { exact: true })).toBeVisible()
  } finally {
    await admin.dispose()
  }
})

test('a user can update profile, change password and anonymize the account', async ({ page }) => {
  const username = `coreuser${Date.now()}`
  const originalPassword = 'Password@123'
  const newPassword = 'NewPassword@456'
  const nickname = `E2E昵称${Date.now()}`

  await apiJson(page.request, 'POST', '/api/v1/auth/register', {
    username,
    password: originalPassword,
    passwordConfirmation: originalPassword,
  })
  await apiLogin(page.request, username, originalPassword)

  await page.goto('/app/account')
  await expect(page.getByRole('heading', { name: '账号设置' })).toBeVisible()

  await page.getByPlaceholder('讨论区展示名称').fill(nickname)
  await page.getByRole('button', { name: '保存昵称' }).click()
  await expect(page.getByText('昵称已更新')).toBeVisible()

  const passwordCard = page.locator('.card', { hasText: '修改密码' })
  await passwordCard.locator('input').nth(0).fill(originalPassword)
  await passwordCard.locator('input').nth(1).fill(newPassword)
  await passwordCard.locator('input').nth(2).fill(newPassword)
  await passwordCard.getByRole('button', { name: '修改密码' }).click()
  await expect(page.getByText('密码已修改，其他会话已失效')).toBeVisible()

  await page.getByRole('button', { name: '退出' }).click()
  await expect(page).toHaveURL(/\/app\/login$/)
  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(newPassword)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/app$/)

  await page.goto('/app/account')
  page.once('dialog', (dialog) => dialog.accept())
  await page.getByRole('button', { name: '注销账号' }).click()
  await expect(page).toHaveURL(/\/app\/login$/)

  await page.getByPlaceholder('请输入用户名').fill(username)
  await page.getByPlaceholder('请输入密码').fill(newPassword)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page.locator('.el-alert')).toBeVisible()
})

test('a new user can use the general AI assistant', async ({ page }) => {
  test.setTimeout(120_000)
  const username = `coreai${Date.now()}`
  const password = 'Password@123'
  await apiJson(page.request, 'POST', '/api/v1/auth/register', {
    username,
    password,
    passwordConfirmation: password,
  })
  await apiLogin(page.request, username, password)

  await page.goto('/app/assistant')
  await expect(page.getByRole('heading', { name: '通用辅导' })).toBeVisible()
  await expect(page.getByText('AI 额度')).toBeVisible()

  await page
    .getByPlaceholder('输入问题，Enter 发送，Shift+Enter 换行')
    .fill('请给我一个两周的复习安排建议。')
  await page.getByRole('button', { name: '发送' }).click()
  await expect(page.getByText(/\[mock\] 已收到你的问题/)).toBeVisible({ timeout: 60_000 })
})

test('an administrator can author, submit and publish a question through the UI', async ({
  page,
}) => {
  test.setTimeout(120_000)
  await apiLogin(page.request, 'admin', 'Admin@12345')
  const stem = `e2e authored question ${Date.now()}`

  await page.goto('/admin/questions/new')
  await expect(page.getByRole('heading', { name: '新建题目' })).toBeVisible()
  await page.locator('.el-form-item', { hasText: '题干' }).locator('textarea').fill(stem)
  await page
    .locator('.el-form-item', { hasText: '官方解析' })
    .locator('textarea')
    .fill(`Explanation for ${stem}`)
  await page
    .locator('.el-form-item', { hasText: '来源说明' })
    .locator('textarea')
    .fill('E2E source')

  const optionInputs = page.getByPlaceholder('选项内容')
  await optionInputs.nth(0).fill('Option A')
  await optionInputs.nth(1).fill('Option B')

  await page.getByRole('button', { name: '创建草稿' }).click()
  await expect(page.getByText('草稿已创建')).toBeVisible()
  await page.getByRole('button', { name: '送审' }).click()
  await expect(page.getByText('已送审')).toBeVisible()

  await page.goto('/admin/review')
  await expect(page.getByRole('heading', { name: '待审核题目' })).toBeVisible()
  const reviewCard = page.locator('.review-card', { hasText: stem })
  await expect(reviewCard).toBeVisible()
  await reviewCard.getByRole('button', { name: '通过并发布' }).click()
  await expect(page.getByText('已通过并发布')).toBeVisible()

  const published = await apiJson<QuestionSearchPage>(
    page.request,
    'GET',
    `/api/v1/questions/search?keyword=${encodeURIComponent(stem)}`,
  )
  expect(published.total).toBe(1)
  expect(published.items[0]!.stem).toBe(stem)
})

test('an administrator can import a CSV question batch through the UI', async ({ page }) => {
  await apiLogin(page.request, 'admin', 'Admin@12345')
  const stem = `e2e imported ${Date.now()}`
  const csv = [
    'type,subjectCode,chapterCode,stem,options,correctOption,fillAnswer,aliases,explanation,difficulty,source,tags',
    `SINGLE_CHOICE,SUB-DEMO-01,CH-01,${stem},A:Option A|B:Option B,B,,,Imported explanation,MEDIUM,E2E,`,
  ].join('\n')

  await page.goto('/admin/import')
  await expect(page.getByRole('heading', { name: '题目导入' })).toBeVisible()
  await page.locator('input[type="file"]').setInputFiles({
    name: 'core-import.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf8'),
  })
  await page.getByRole('button', { name: '上传并校验' }).click()
  await expect(page.getByText('校验完成，请确认后导入')).toBeVisible()

  await page.getByRole('button', { name: '确认导入' }).click()
  await expect(page.getByText('导入成功，题目已创建为草稿，仍需送审后才能发布')).toBeVisible()

  const drafts = await apiJson<QuestionSearchPage>(
    page.request,
    'GET',
    `/api/v1/admin/questions?keyword=${encodeURIComponent(stem)}&status=DRAFT`,
  )
  expect(drafts.items.some((item) => item.stem === stem)).toBe(true)
})

test('an administrator can create a catalog tag through the UI', async ({ page }) => {
  await apiLogin(page.request, 'admin', 'Admin@12345')
  const tagName = `E2E标签${Date.now()}`

  await page.goto('/admin/catalog')
  await expect(page.getByRole('heading', { name: '目录与标签' })).toBeVisible()
  await page.getByRole('button', { name: '新增标签' }).click()

  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('标签名称').fill(tagName)
  await dialog.getByRole('button', { name: '保存' }).click()
  await expect(page.getByText('标签已保存。')).toBeVisible()

  const tags = await apiJson<AdminTag[]>(page.request, 'GET', '/api/v1/admin/catalog/tags')
  expect(tags.some((tag) => tag.name === tagName)).toBe(true)
})

import { expect, test } from '@playwright/test'

import { apiJson, apiLogin } from './helpers'

const DEMO_QUESTION_ID = '20000000-0000-0000-0000-000000000001'
const DEMO_QUESTION_VERSION_ID = '21000000-0000-0000-0000-000000000001'

interface DiscussionCreated {
  postId: string
}

interface ReportCreated {
  reportId: string
  status: string
}

test('a student can start a version-bound discussion and reply to it', async ({ page }) => {
  await apiLogin(page.request, 'student01', 'Student@12345')
  const title = `E2E 讨论 ${Date.now()}`

  await page.goto(`/app/questions/${DEMO_QUESTION_ID}/discussions`)
  await expect(page.getByRole('heading', { name: '讨论与题解' })).toBeVisible()
  await expect(page.getByText('TCP 可靠传输怎么判断？')).toBeVisible()

  await page.getByPlaceholder('用一个清晰的标题描述你的问题').fill(title)
  await page
    .getByPlaceholder('补充你的思路、疑问或题解')
    .fill('这是端到端测试创建的讨论，用来验证版本绑定与回复。')
  await page.getByRole('button', { name: '发布讨论' }).click()

  const postTitle = page.getByRole('link', { name: title })
  await expect(postTitle).toBeVisible()
  await postTitle.click()

  await expect(page).toHaveURL(/\/app\/discussions\/.+/)
  await expect(page.getByText('题目版本 v1')).toBeVisible()
  await expect(page.getByRole('heading', { name: title })).toBeVisible()
})

test('an administrator resolves a report and the reporter sees a notification', async ({
  page,
  browser,
}) => {
  test.setTimeout(120_000)
  const reason = `E2E 举报 ${Date.now()}`

  await apiLogin(page.request, 'student01', 'Student@12345')
  const post = await apiJson<DiscussionCreated>(
    page.request,
    'POST',
    `/api/v1/questions/${DEMO_QUESTION_ID}/discussions`,
    {
      questionVersionId: DEMO_QUESTION_VERSION_ID,
      title: `待处理讨论 ${Date.now()}`,
      body: '这条讨论会被用于举报与治理端到端测试。',
    },
  )
  const report = await apiJson<ReportCreated>(
    page.request,
    'POST',
    `/api/v1/discussions/${post.postId}/report`,
    { reason },
  )
  expect(report.status).toBe('OPEN')

  await apiLogin(page.request, 'admin', 'Admin@12345')
  await page.goto('/admin/moderation')
  await expect(page.getByRole('heading', { name: '举报与纠错处理' })).toBeVisible()

  const row = page.locator('.el-table__row', { hasText: reason })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '处理' }).click()

  const dialog = page.getByRole('dialog')
  await dialog.getByText('采纳并处理').click()
  await dialog.locator('.el-select').click()
  await page.locator('.el-select-dropdown__item', { hasText: '隐藏内容' }).click()
  await dialog.getByPlaceholder('说明处理依据，便于审计').fill('端到端测试：隐藏违规内容')
  await dialog.getByRole('button', { name: '确认处理' }).click()

  await expect(page.getByText(/举报已处理/)).toBeVisible()

  const studentContext = await browser.newContext()
  const studentPage = await studentContext.newPage()
  await apiLogin(studentPage.request, 'student01', 'Student@12345')

  await studentPage.goto('/app/notifications')
  await expect(studentPage.getByRole('heading', { name: '站内通知' })).toBeVisible()
  await expect(studentPage.getByText('举报处理结果').first()).toBeVisible()
  await studentPage.getByRole('button', { name: '全部标记已读' }).click()
  await expect(studentPage.getByText(/已将 .* 条通知标记为已读/)).toBeVisible()

  await studentPage.goto(`/app/discussions/${post.postId}`)
  await expect(studentPage.getByText('讨论不存在或已被隐藏。')).toBeVisible()

  await studentContext.close()
})

test('the administrator dashboard, user list and audit query render P2 data', async ({ page }) => {
  await apiLogin(page.request, 'admin', 'Admin@12345')

  await page.goto('/admin/dashboard')
  await expect(page.getByRole('heading', { name: '学习与治理概览' })).toBeVisible()
  await expect(page.getByText('近 7 天活跃用户')).toBeVisible()
  await expect(page.getByText('练习完成率')).toBeVisible()
  await expect(page.getByText('客观题错误率')).toBeVisible()
  await expect(page.getByText('填空自动匹配率')).toBeVisible()
  await expect(page.getByText('自评通过率')).toBeVisible()

  await page.goto('/admin/users')
  await expect(page.getByRole('heading', { name: '用户管理' })).toBeVisible()
  await page.getByPlaceholder('用户名或昵称').fill('student01')
  await page.getByRole('button', { name: '搜索' }).click()
  await expect(page.getByText('student01').first()).toBeVisible()

  await page.goto('/admin/audit-logs')
  await expect(page.getByRole('heading', { name: '审计日志' })).toBeVisible()
  await page.getByPlaceholder('操作，如 USER_DISABLE').fill('CONTENT_REPORT_RESOLVE')
  await page.getByRole('button', { name: '查询' }).click()
  await expect(
    page.locator('.el-table__row', { hasText: 'CONTENT_REPORT_RESOLVE' }).first(),
  ).toBeVisible()
})

test('an administrator sets a temporary password the user can sign in with', async ({
  page,
  browser,
}) => {
  const username = `reset${Date.now()}${Math.floor(Math.random() * 1000)}`
  const originalPassword = 'Original@123'
  const temporaryPassword = 'Reset@456'

  await apiJson(page.request, 'POST', '/api/v1/auth/register', {
    username,
    password: originalPassword,
    passwordConfirmation: originalPassword,
  })
  await apiLogin(page.request, 'admin', 'Admin@12345')

  await page.goto('/admin/users')
  await page.getByPlaceholder('用户名或昵称').fill(username)
  await page.getByRole('button', { name: '搜索' }).click()

  const row = page.locator('.el-table__row', { hasText: username })
  await expect(row).toBeVisible()
  await row.getByRole('button', { name: '重置密码' }).click()

  const dialog = page.getByRole('dialog')
  await dialog.getByPlaceholder('新的临时密码（至少 8 个字符）').fill(temporaryPassword)
  await dialog.getByPlaceholder('再次输入临时密码').fill(temporaryPassword)
  await dialog.getByRole('button', { name: '确认重置' }).click()

  await expect(page.getByText(/密码已重置/)).toBeVisible()
  await expect(page.getByText(temporaryPassword)).toHaveCount(0)

  const userContext = await browser.newContext()
  const userPage = await userContext.newPage()
  const session = await apiLogin(userPage.request, username, temporaryPassword)
  expect(session.username).toBe(username)
  await userContext.close()
})

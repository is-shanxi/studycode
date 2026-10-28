import { expect, request as playwrightRequest, test } from '@playwright/test'

import type { AdminQuestionDetail, CatalogTree, ResultView, SessionView } from './types'
import { apiJson, apiLogin } from './helpers'

test('historical records keep the old version after a new version is published', async () => {
  test.setTimeout(120_000)
  const baseURL = 'http://localhost:5173'
  const admin = await playwrightRequest.newContext({ baseURL })
  const student = await playwrightRequest.newContext({ baseURL })

  try {
    await apiLogin(admin, 'admin', 'Admin@12345')
    await apiLogin(student, 'student01', 'Student@12345')

    const tree = await apiJson<CatalogTree>(admin, 'GET', '/api/v1/catalog/tree')
    const subject = tree.certificates[0]?.subjects[0]
    expect(subject).toBeTruthy()
    const subjectId = subject!.id
    const chapterId = subject!.chapters[0]!.id
    const stem = `e2e versioned ${Date.now()}`

    const created = await apiJson<AdminQuestionDetail>(admin, 'POST', '/api/v1/admin/questions', {
      subjectId,
      chapterId,
      type: 'SINGLE_CHOICE',
      stem,
      explanation: `Explanation for ${stem}`,
      difficulty: 'MEDIUM',
      sourceText: 'E2E source',
      options: [
        { key: 'A', content: 'A option', correct: false },
        { key: 'B', content: 'B option', correct: true },
      ],
      aliases: [],
      tagIds: [],
    })
    const questionId = created.questionId
    const firstVersionId = created.versions[0]!.versionId
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${firstVersionId}/submit-review`)
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${firstVersionId}/approve`, {
      publishNow: true,
    })

    const session = await apiJson<SessionView>(
      student,
      'POST',
      '/api/v1/practice-sessions',
      { mode: 'NORMAL', filters: { subjectId }, requestedCount: 100 },
      { 'Idempotency-Key': `e2e-version-${Date.now()}` },
    )
    const originalItem = session.items.find((item) => item.question.questionId === questionId)
    expect(originalItem).toBeTruthy()
    expect(originalItem!.question.questionVersionId).toBe(firstVersionId)

    for (const item of session.items) {
      const body =
        item.question.type === 'SINGLE_CHOICE'
          ? { answerRevision: 0, answer: { selectedOptionId: item.question.options[0]!.id } }
          : { answerRevision: 0, answer: { text: 'e2e answer' } }
      await apiJson(
        student,
        'PUT',
        `/api/v1/practice-sessions/${session.sessionId}/items/${item.itemId}/answer`,
        body,
      )
    }
    await apiJson(
      student,
      'POST',
      `/api/v1/practice-sessions/${session.sessionId}/submit`,
      undefined,
      { 'Idempotency-Key': `e2e-submit-${Date.now()}` },
    )

    const cloned = await apiJson<AdminQuestionDetail>(
      admin,
      'POST',
      `/api/v1/admin/questions/${questionId}/versions`,
    )
    const secondVersionId = cloned.versions[0]!.versionId
    await apiJson(admin, 'PUT', `/api/v1/admin/question-versions/${secondVersionId}`, {
      stem: `${stem} v2`,
    })
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${secondVersionId}/submit-review`)
    await apiJson(admin, 'POST', `/api/v1/admin/question-versions/${secondVersionId}/approve`, {
      publishNow: true,
    })

    const record = await apiJson<ResultView>(
      student,
      'GET',
      `/api/v1/me/records/${session.sessionId}`,
    )
    const historical = record.items.find((item) => item.questionId === questionId)
    expect(historical).toBeTruthy()
    expect(historical!.questionVersionId).toBe(firstVersionId)
    expect(historical!.stem).toBe(stem)

    const newSession = await apiJson<SessionView>(
      student,
      'POST',
      '/api/v1/practice-sessions',
      { mode: 'NORMAL', filters: { subjectId }, requestedCount: 100 },
      { 'Idempotency-Key': `e2e-version-2-${Date.now()}` },
    )
    const newItem = newSession.items.find((item) => item.question.questionId === questionId)
    expect(newItem).toBeTruthy()
    expect(newItem!.question.questionVersionId).toBe(secondVersionId)
    expect(newItem!.question.stem).toContain('v2')
  } finally {
    await admin.dispose()
    await student.dispose()
  }
})

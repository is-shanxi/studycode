import { describe, expect, it } from 'vitest'

import type { SessionItemView, SessionView } from '@/features/api/types'
import {
  answeredCount,
  canSubmit,
  compositionLabel,
  containsAnswerLeak,
} from '@/features/practice/session'

function item(ordinal: number, answered: boolean): SessionItemView {
  return {
    itemId: `item-${ordinal}`,
    ordinal,
    sourceType: 'ORIGINAL',
    selectionBucket: 'UNSEEN',
    question: {
      questionId: `question-${ordinal}`,
      questionVersionId: `version-${ordinal}`,
      type: 'SINGLE_CHOICE',
      stem: `stem ${ordinal}`,
      difficulty: 'MEDIUM',
      tags: [],
      options: [
        { id: `option-${ordinal}-a`, key: 'A', content: 'A' },
        { id: `option-${ordinal}-b`, key: 'B', content: 'B' },
      ],
    },
    ...(answered ? { answer: { selectedOptionId: `option-${ordinal}-a`, answerRevision: 1 } } : {}),
  }
}

function session(
  status: SessionView['status'],
  items: SessionItemView[],
  allowedActions: string[] = status === 'IN_PROGRESS'
    ? ['SAVE_ANSWER', 'SUBMIT', 'ABANDON']
    : ['VIEW_RESULT'],
): SessionView {
  return {
    sessionId: 'session-1',
    mode: 'NORMAL',
    status,
    rowVersion: 0,
    requestedCount: items.length,
    actualCount: items.length,
    composition: {
      original: items.length,
      aiVariant: 0,
      buckets: { unseen: items.length, wrong: 0, normal: 0 },
    },
    allowedActions,
    items,
  }
}

describe('practice session helpers', () => {
  it('detects answer fields that must never appear before submission', () => {
    expect(
      containsAnswerLeak({
        items: [
          {
            question: {
              stem: 'visible',
              options: [{ id: '1', key: 'A', content: 'option' }],
            },
          },
        ],
      }),
    ).toBe(false)

    expect(containsAnswerLeak({ items: [{ isCorrect: true }] })).toBe(true)
    expect(containsAnswerLeak({ nested: { referenceAnswer: 'SYN' } })).toBe(true)
    expect(containsAnswerLeak({ options: [{ explanation: 'secret' }] })).toBe(true)
  })

  it('allows submission only when every item has an answer', () => {
    expect(canSubmit(session('IN_PROGRESS', [item(1, true), item(2, true)]))).toBe(true)
    expect(canSubmit(session('IN_PROGRESS', [item(1, true), item(2, false)]))).toBe(false)
    expect(canSubmit(session('COMPLETED', [item(1, true), item(2, true)]))).toBe(false)
    expect(answeredCount(session('IN_PROGRESS', [item(1, true), item(2, false)]))).toBe(1)
  })

  it('treats server allowedActions as the submission authority', () => {
    const answered = [item(1, true), item(2, true)]
    expect(canSubmit(session('IN_PROGRESS', answered, []))).toBe(false)
    expect(canSubmit(session('IN_PROGRESS', answered, ['SUBMIT']))).toBe(true)
  })

  it('exposes the actual composition, including bucket fallback counts', () => {
    const value = session('IN_PROGRESS', [item(1, false), item(2, false)])
    expect(compositionLabel(value)).toContain('原题 2')
    expect(compositionLabel(value)).toContain('未见 2')
  })
})

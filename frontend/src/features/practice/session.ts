import type { SessionView } from '@/features/api/types'

export const FORBIDDEN_ANSWER_KEYS = [
  'isCorrect',
  'correctOptionId',
  'referenceAnswer',
  'explanation',
  'aliases',
  'fillReferenceAnswer',
] as const

export function containsAnswerLeak(value: unknown): boolean {
  if (value === null || typeof value !== 'object') {
    return false
  }
  if (Array.isArray(value)) {
    return value.some(containsAnswerLeak)
  }
  const record = value as Record<string, unknown>
  return Object.entries(record).some(
    ([key, child]) =>
      (FORBIDDEN_ANSWER_KEYS as readonly string[]).includes(key) || containsAnswerLeak(child),
  )
}

export function answeredCount(session: SessionView): number {
  return session.items.filter((item) => item.answer !== undefined).length
}

export function canSubmit(session: SessionView): boolean {
  return (
    session.status === 'IN_PROGRESS' &&
    session.allowedActions.includes('SUBMIT') &&
    session.items.length > 0 &&
    answeredCount(session) === session.items.length
  )
}

export function compositionLabel(session: SessionView): string {
  const { original, aiVariant, buckets } = session.composition
  return `原题 ${original} / AI 变式 ${aiVariant} · 未见 ${buckets.unseen} / 错题 ${buckets.wrong} / 已做 ${buckets.normal}`
}

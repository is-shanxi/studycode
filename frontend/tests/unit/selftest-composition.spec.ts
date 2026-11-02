import { describe, expect, it } from 'vitest'

import {
  DEFAULT_AI_VARIANT_RATIO,
  DEFAULT_ORIGINAL_RATIO,
  buildSelfTestRequest,
  normalizeComposition,
} from '@/features/selftest/composition'

describe('self-test composition helpers', () => {
  it('defaults to a 6:4 original and AI-variant mix', () => {
    expect(normalizeComposition()).toEqual({
      originalRatio: DEFAULT_ORIGINAL_RATIO,
      aiVariantRatio: DEFAULT_AI_VARIANT_RATIO,
    })
  })

  it('normalizes ratios that do not sum to one', () => {
    expect(normalizeComposition(0.6, 0.2)).toEqual({
      originalRatio: 0.75,
      aiVariantRatio: 0.25,
    })
  })

  it('rejects negative, over-one and zero-sum ratios', () => {
    expect(() => normalizeComposition(-0.1, 1.1)).toThrow()
    expect(() => normalizeComposition(1.2, 0.2)).toThrow()
    expect(() => normalizeComposition(0, 0)).toThrow()
    expect(() => normalizeComposition(Number.NaN, 0.4)).toThrow()
  })

  it('builds a SELF_TEST request with the normalized composition', () => {
    const request = buildSelfTestRequest({ subjectId: 'subject-1', historyDays: 90 }, 10, 0.6, 0.4)

    expect(request.mode).toBe('SELF_TEST')
    expect(request.requestedCount).toBe(10)
    expect(request.filters.subjectId).toBe('subject-1')
    expect(request.composition).toEqual({
      originalRatio: 0.6,
      aiVariantRatio: 0.4,
    })
  })

  it('rejects an out-of-range requested count', () => {
    expect(() => buildSelfTestRequest({}, 0)).toThrow()
    expect(() => buildSelfTestRequest({}, 101)).toThrow()
  })
})

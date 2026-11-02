import type { CompositionSpec, CreateSessionRequest, PracticeFilters } from '@/features/api/types'

export const DEFAULT_ORIGINAL_RATIO = 0.6
export const DEFAULT_AI_VARIANT_RATIO = 0.4

export function normalizeComposition(
  originalRatio: number = DEFAULT_ORIGINAL_RATIO,
  aiVariantRatio: number = DEFAULT_AI_VARIANT_RATIO,
): CompositionSpec {
  if (
    !Number.isFinite(originalRatio) ||
    !Number.isFinite(aiVariantRatio) ||
    originalRatio < 0 ||
    aiVariantRatio < 0
  ) {
    throw new Error('自测比例必须为非负数。')
  }
  if (originalRatio > 1 || aiVariantRatio > 1) {
    throw new Error('单个自测比例不能大于 1。')
  }
  const total = originalRatio + aiVariantRatio
  if (total <= 0) {
    throw new Error('自测比例之和必须大于 0。')
  }
  return {
    originalRatio: roundRatio(originalRatio / total),
    aiVariantRatio: roundRatio(aiVariantRatio / total),
  }
}

function roundRatio(value: number): number {
  return Math.round(value * 10000) / 10000
}

export function buildSelfTestRequest(
  filters: PracticeFilters,
  requestedCount: number,
  originalRatio?: number,
  aiVariantRatio?: number,
): CreateSessionRequest {
  if (!Number.isInteger(requestedCount) || requestedCount < 1 || requestedCount > 100) {
    throw new Error('自测题量必须在 1 到 100 之间。')
  }
  return {
    mode: 'SELF_TEST',
    filters,
    requestedCount,
    composition: normalizeComposition(originalRatio, aiVariantRatio),
  }
}

export interface OptionSafe {
  id: string
  key: string
  content: string
}

export interface PublicQuestionView {
  questionId: string
  questionVersionId: string
  type: 'SINGLE_CHOICE' | 'SINGLE_BLANK'
  stem: string
  difficulty: string
  options: OptionSafe[]
}

export interface SessionItemView {
  itemId: string
  ordinal: number
  question: PublicQuestionView
}

export interface CompositionView {
  original: number
  aiVariant: number
  buckets: { unseen: number; wrong: number; normal: number }
}

export interface SessionView {
  sessionId: string
  mode: string
  status: string
  actualCount: number
  requestedCount?: number
  composition?: CompositionView
  allowedActions?: string[]
  items: SessionItemView[]
}

export interface ResultItemView {
  itemId: string
  questionId: string
  questionVersionId: string
  type: string
  stem: string
  selfAssessmentResult?: string
}

export interface ResultView {
  sessionId: string
  mode: string
  status: string
  requestedCount: number
  actualCount: number
  composition?: CompositionView
  allowedActions?: string[]
  items: ResultItemView[]
}

export interface AdminVersionView {
  versionId: string
  versionNo: number
  status: string
  stem: string
}

export interface AdminQuestionDetail {
  questionId: string
  subjectId: string
  chapterId?: string
  publishedVersionId?: string
  versions: AdminVersionView[]
}

export interface CatalogTree {
  certificates: Array<{
    id: string
    subjects: Array<{
      id: string
      chapters: Array<{ id: string }>
    }>
  }>
}

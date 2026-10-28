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

export interface SessionView {
  sessionId: string
  mode: string
  status: string
  actualCount: number
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
  status: string
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

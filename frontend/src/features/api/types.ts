export type Role = 'USER' | 'ADMIN'
export type QuestionType = 'SINGLE_CHOICE' | 'SINGLE_BLANK'
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type SessionMode = 'NORMAL' | 'SELF_TEST'
export type SessionStatus =
  'IN_PROGRESS' | 'SUBMITTED' | 'PENDING_SELF_ASSESSMENT' | 'COMPLETED' | 'ABANDONED'
export type QuestionStatus = 'DRAFT' | 'PENDING_REVIEW' | 'PUBLISHED' | 'OFFLINE'
export type MatchResult = 'NOT_APPLICABLE' | 'MATCH' | 'MISMATCH'
export type AssessmentResult = 'PASS' | 'FAIL'

export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  total: number
}

export interface SessionUser {
  userId: string
  username: string
  nickname?: string
  roles: Role[]
}

export interface Profile {
  userId: string
  username: string
  nickname?: string
  status: string
  roles: Role[]
}

export interface ChapterNode {
  id: string
  code: string
  name: string
}

export interface SubjectNode {
  id: string
  code: string
  name: string
  chapters: ChapterNode[]
}

export interface CertificateNode {
  id: string
  code: string
  name: string
  subjects: SubjectNode[]
}

export interface CatalogTree {
  certificates: CertificateNode[]
}

export interface Tag {
  id: string
  name: string
}

export interface TagRef {
  id: string
  name: string
}

export interface ChapterRef {
  id: string
  name: string
}

export interface QuestionSummary {
  questionId: string
  questionVersionId: string
  type: QuestionType
  stem: string
  difficulty: Difficulty
  chapter?: ChapterRef
  tags: TagRef[]
  status: string
}

export interface OptionSafe {
  id: string
  key: string
  content: string
}

export interface PublicQuestionView {
  questionId: string
  questionVersionId: string
  type: QuestionType
  stem: string
  difficulty: Difficulty
  chapter?: ChapterRef
  tags: TagRef[]
  options: OptionSafe[]
}

export interface SearchParams {
  certificateId?: string
  subjectId?: string
  chapterIds?: string[]
  tagIds?: string[]
  keyword?: string
  difficulty?: Difficulty[]
  type?: QuestionType
  page?: number
  size?: number
}

export interface PracticeFilters {
  certificateId?: string
  subjectId?: string
  chapterIds?: string[]
  tagIds?: string[]
  difficulty?: Difficulty[]
  historyDays?: number
}

export interface CompositionSpec {
  originalRatio: number
  aiVariantRatio: number
}

export interface CreateSessionRequest {
  mode: SessionMode
  filters: PracticeFilters
  requestedCount: number
  composition?: CompositionSpec
}

export interface AnswerView {
  selectedOptionId?: string
  text?: string
  answerRevision: number
}

export interface SessionItemView {
  itemId: string
  ordinal: number
  sourceType: 'ORIGINAL' | 'AI_VARIANT'
  selectionBucket: 'UNSEEN' | 'WRONG' | 'NORMAL'
  question: PublicQuestionView
  answer?: AnswerView
  autoMatchResult?: MatchResult
  selfAssessmentResult?: AssessmentResult
  finalResult?: AssessmentResult
}

export interface BucketCounts {
  unseen: number
  wrong: number
  normal: number
}

export interface CompositionView {
  original: number
  aiVariant: number
  buckets: BucketCounts
}

export interface SessionView {
  sessionId: string
  mode: SessionMode
  status: SessionStatus
  rowVersion: number
  requestedCount: number
  actualCount: number
  composition: CompositionView
  allowedActions: string[]
  items: SessionItemView[]
}

export interface SaveAnswerRequest {
  answerRevision: number
  answer: {
    selectedOptionId?: string
    text?: string
  }
}

export interface SaveAnswerResponse {
  itemId: string
  answerRevision: number
}

export interface ResultItemView {
  itemId: string
  questionId: string
  questionVersionId: string
  type: QuestionType
  stem: string
  difficulty: Difficulty
  finalResult?: AssessmentResult
  selectedOptionId?: string
  text?: string
  correctOptionId?: string
  referenceAnswer?: string
  explanation?: string
  autoMatchResult?: MatchResult
  selfAssessmentResult?: AssessmentResult
  options: OptionSafe[]
}

export interface ResultView {
  sessionId: string
  mode: SessionMode
  status: SessionStatus
  requestedCount: number
  actualCount: number
  startedAt: string
  submittedAt?: string
  completedAt?: string
  objective: {
    answered: number
    correct: number
  }
  fill: {
    autoMatched: number
    selfPassed: number
    selfFailed: number
  }
  items: ResultItemView[]
}

export interface RecordSummary {
  sessionId: string
  mode: SessionMode
  status: SessionStatus
  requestedCount: number
  actualCount: number
  objectiveCorrect: number
  objectiveAnswered: number
  startedAt: string
  submittedAt?: string
  completedAt?: string
}

export interface FavoriteView {
  questionId: string
  questionVersionId: string
  type: QuestionType
  stem: string
  difficulty: Difficulty
  status: string
  favoritedAt: string
}

export interface WrongQuestionView {
  questionId: string
  questionVersionId: string
  type: QuestionType
  stem: string
  difficulty: Difficulty
  status: string
  lastFailedAt: string
}

export interface AdminQuestionSummary {
  questionId: string
  currentVersionId?: string
  type: QuestionType
  status: QuestionStatus
  stem: string
  difficulty: Difficulty
  updatedAt: string
}

export interface OptionAdmin {
  id: string
  key: string
  content: string
  correct: boolean
}

export interface OptionInput {
  key: string
  content: string
  correct: boolean
}

export interface VersionView {
  versionId: string
  versionNo: number
  status: QuestionStatus
  stem: string
  explanation?: string
  fillReferenceAnswer?: string
  difficulty: Difficulty
  sourceText?: string
  authorId: string
  reviewedBy?: string
  reviewedAt?: string
  publishedAt?: string
  offlineAt?: string
  supersededAt?: string
  options: OptionAdmin[]
  aliases: string[]
  tags: TagRef[]
}

export interface AdminQuestionDetail {
  questionId: string
  subjectId: string
  chapterId?: string
  type: QuestionType
  status: QuestionStatus
  publishedVersionId?: string
  versions: VersionView[]
}

export interface CreateQuestionPayload {
  subjectId: string
  chapterId: string
  type: QuestionType
  stem: string
  explanation?: string
  fillReferenceAnswer?: string | null
  difficulty: Difficulty
  sourceText?: string
  options?: OptionInput[]
  aliases?: string[]
  tagIds?: string[]
}

export interface UpdateDraftPayload {
  chapterId?: string
  stem?: string
  explanation?: string
  fillReferenceAnswer?: string | null
  difficulty?: Difficulty
  sourceText?: string
  options?: OptionInput[]
  aliases?: string[]
  tagIds?: string[]
}

export interface QuestionStatusResponse {
  questionId: string
  versionId: string
  status: QuestionStatus
}

export interface ImportErrorView {
  rowNumber: number
  field?: string
  code: string
  message: string
}

export interface ImportBatchView {
  batchId: string
  status: string
  fileName: string
  totalRows: number
  validRows: number
  invalidRows: number
  previewExpiresAt?: string
  confirmedAt?: string
  errors: ImportErrorView[]
}

export type ReportStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED' | 'REJECTED'
export type ReportSource = 'CONTENT' | 'CORRECTION'

export interface DiscussionPostSummary {
  postId: string
  questionId: string
  questionVersionId: string
  versionNo: number
  versionCurrent: boolean
  title: string
  bodyPreview?: string
  authorUserId: string
  authorName: string
  status: string
  commentCount: number
  createdAt: string
}

export interface DiscussionCommentView {
  commentId: string
  parentCommentId?: string
  quotedCommentId?: string
  quotedPreview?: string
  authorUserId: string
  authorName: string
  body: string
  status: string
  createdAt: string
}

export interface DiscussionPostDetail {
  postId: string
  questionId: string
  questionVersionId: string
  versionNo: number
  versionCurrent: boolean
  title: string
  body: string
  authorUserId: string
  authorName: string
  status: string
  createdAt: string
  comments: DiscussionCommentView[]
}

export interface CreateDiscussionPayload {
  questionVersionId: string
  title: string
  body: string
}

export interface CreateCommentPayload {
  parentCommentId?: string | null
  quotedCommentId?: string | null
  body: string
}

export interface ReportCreated {
  reportId: string
  status: string
}

export interface NotificationView {
  notificationId: string
  type: string
  title: string
  body: string
  payload?: string
  readAt?: string
  createdAt: string
}

export interface MarkAllReadResponse {
  updated: number
}

export interface AnnouncementResponse {
  recipientCount: number
}

export interface AdminUserView {
  userId: string
  username: string
  nickname?: string
  status: 'ACTIVE' | 'DISABLED' | 'DELETED_ANONYMIZED'
  roles: Role[]
  createdAt: string
}

export interface ResetPasswordResponse {
  userId: string
  username: string
  temporaryPassword: string
}

export interface ModerationReportView {
  reportId: string
  source: ReportSource
  targetType: string
  targetId: string
  questionVersionId?: string
  reason: string
  status: ReportStatus
  reporterUserId: string
  reporterName: string
  handledBy?: string
  handledAt?: string
  createdAt: string
}

export interface ResolveReportPayload {
  source: ReportSource
  resolution: 'PROCESSING' | 'RESOLVED' | 'REJECTED'
  moderationAction?: 'HIDE' | 'REMOVE'
  note?: string
}

export interface ResolveReportResponse {
  reportId: string
  source: ReportSource
  status: ReportStatus
  targetStatus?: string
}

export interface DashboardView {
  activeUsers: number
  activeUsersLast7Days: number
  totalPracticeSessions: number
  completedPracticeSessions: number
  practiceCompletionRate: number
  objectiveGradedItems: number
  objectiveFailedItems: number
  questionErrorRate: number
  fillAnswers: number
  autoMatchedAnswers: number
  autoMatchRate: number
  selfAssessedItems: number
  selfPassedItems: number
  selfFailedItems: number
  selfAssessmentPassRate: number
  pendingReviewVersions: number
  openReports: number
  aiRequestCount: number
  aiCost: number
}

export interface AuditLogView {
  auditLogId: string
  actorUserId: string
  action: string
  targetType: string
  targetId?: string
  outcome: 'SUCCESS' | 'FAILED' | 'REJECTED'
  summary?: string
  traceId?: string
  createdAt: string
}

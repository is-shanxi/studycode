import type { RouteRecordRaw } from 'vue-router'

export const adminRoutes: RouteRecordRaw[] = [
  {
    path: '/admin',
    component: () => import('@/admin/layout/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'admin-home',
        component: () => import('@/admin/pages/AdminHomePage.vue'),
      },
      {
        path: 'dashboard',
        name: 'admin-dashboard',
        component: () => import('@/admin/pages/DashboardPage.vue'),
      },
      {
        path: 'users',
        name: 'admin-users',
        component: () => import('@/admin/pages/UserManagementPage.vue'),
      },
      {
        path: 'moderation',
        name: 'admin-moderation',
        component: () => import('@/admin/pages/ModerationPage.vue'),
      },
      {
        path: 'audit-logs',
        name: 'admin-audit-logs',
        component: () => import('@/admin/pages/AuditLogPage.vue'),
      },
      {
        path: 'catalog',
        name: 'admin-catalog',
        component: () => import('@/admin/pages/CatalogManagementPage.vue'),
      },
      {
        path: 'ai',
        name: 'admin-ai',
        component: () => import('@/admin/pages/AiGenerationPage.vue'),
      },
      {
        path: 'ai/candidates/:batchId',
        name: 'admin-ai-candidates',
        component: () => import('@/admin/pages/AiCandidateReviewPage.vue'),
      },
      {
        path: 'questions',
        name: 'admin-questions',
        component: () => import('@/admin/pages/QuestionListPage.vue'),
      },
      {
        path: 'questions/new',
        name: 'admin-question-new',
        component: () => import('@/admin/pages/QuestionEditorPage.vue'),
      },
      {
        path: 'questions/:questionId/edit',
        name: 'admin-question-edit',
        component: () => import('@/admin/pages/QuestionEditorPage.vue'),
      },
      {
        path: 'review',
        name: 'admin-review',
        component: () => import('@/admin/pages/QuestionReviewPage.vue'),
      },
      {
        path: 'import',
        name: 'admin-import',
        component: () => import('@/admin/pages/QuestionImportPage.vue'),
      },
    ],
  },
]

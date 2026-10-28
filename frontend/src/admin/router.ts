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

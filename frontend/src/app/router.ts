import type { RouteRecordRaw } from 'vue-router'

export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/app/login',
    name: 'app-login',
    component: () => import('@/app/pages/LoginPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/app/register',
    name: 'app-register',
    component: () => import('@/app/pages/RegisterPage.vue'),
    meta: { guestOnly: true },
  },
  {
    path: '/app',
    component: () => import('@/app/layout/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'app-home',
        component: () => import('@/app/pages/AppHomePage.vue'),
      },
      {
        path: 'library',
        name: 'app-library',
        component: () => import('@/app/pages/QuestionLibraryPage.vue'),
      },
      {
        path: 'questions/:questionId/discussions',
        name: 'app-discussions',
        component: () => import('@/app/pages/DiscussionListPage.vue'),
      },
      {
        path: 'discussions/:postId',
        name: 'app-discussion-detail',
        component: () => import('@/app/pages/DiscussionDetailPage.vue'),
      },
      {
        path: 'notifications',
        name: 'app-notifications',
        component: () => import('@/app/pages/NotificationPage.vue'),
      },
      {
        path: 'practice/:sessionId',
        name: 'app-practice',
        component: () => import('@/app/pages/PracticeSessionPage.vue'),
      },
      {
        path: 'practice/:sessionId/fill/:itemId',
        name: 'app-fill',
        component: () => import('@/app/pages/FillQuestionPage.vue'),
      },
      {
        path: 'practice/:sessionId/result',
        name: 'app-result',
        component: () => import('@/app/pages/PracticeResultPage.vue'),
      },
      {
        path: 'account',
        name: 'app-account',
        component: () => import('@/app/pages/AccountPage.vue'),
      },
      {
        path: 'records',
        name: 'app-records',
        component: () => import('@/app/pages/RecordListPage.vue'),
      },
      {
        path: 'records/:sessionId',
        name: 'app-record-detail',
        component: () => import('@/app/pages/RecordDetailPage.vue'),
      },
      {
        path: 'favorites',
        name: 'app-favorites',
        component: () => import('@/app/pages/FavoritePage.vue'),
      },
      {
        path: 'wrong-questions',
        name: 'app-wrong',
        component: () => import('@/app/pages/WrongQuestionPage.vue'),
      },
    ],
  },
]

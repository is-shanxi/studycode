import type { RouteRecordRaw } from 'vue-router'

export const appRoutes: RouteRecordRaw[] = [
  {
    path: '/app/login',
    name: 'app-login',
    component: () => import('@/app/pages/LoginPage.vue'),
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
    ],
  },
]

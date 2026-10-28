import {
  createRouter,
  createWebHistory,
  type RouteRecordRaw,
  type Router,
  type RouterHistory,
} from 'vue-router'

import { adminRoutes } from '@/admin/router'
import { appRoutes } from '@/app/router'
import { useSessionStore } from '@/features/auth/store'

const notFoundRoute: RouteRecordRaw = {
  path: '/:pathMatch(.*)*',
  name: 'not-found',
  component: () => import('@/app/pages/NotFoundPage.vue'),
}

export const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'app-home' } },
  ...appRoutes,
  ...adminRoutes,
  notFoundRoute,
]

export function createAppRouter(history: RouterHistory = createWebHistory()): Router {
  const router = createRouter({ history, routes })

  router.beforeEach(async (to) => {
    const session = useSessionStore()
    if (session.status === 'unknown') {
      await session.fetchCurrentUser()
    }

    if (to.meta.requiresAuth && !session.isAuthenticated) {
      return { name: 'app-login', query: { redirect: to.fullPath } }
    }
    if (to.meta.guestOnly && session.isAuthenticated) {
      return { name: 'app-home' }
    }
    if (to.meta.requiresAdmin && !session.isAdmin) {
      return { name: 'app-home' }
    }
    return true
  })

  return router
}

export const router = createAppRouter()

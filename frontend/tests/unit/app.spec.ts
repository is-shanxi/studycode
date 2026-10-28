import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter, type RouteRecordRaw } from 'vue-router'

import App from '@/App.vue'
import { adminRoutes } from '@/admin/router'
import { appRoutes } from '@/app/router'
import { useSessionStore } from '@/features/auth/store'

const routes: RouteRecordRaw[] = [
  { path: '/', redirect: { name: 'app-home' } },
  ...appRoutes,
  ...adminRoutes,
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: { template: '<div>Not found</div>' },
  },
]

async function mountAt(path: string) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  await router.isReady()

  const pinia = createPinia()
  setActivePinia(pinia)
  useSessionStore().status = 'anonymous'

  const wrapper = mount(App, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

describe('App shell', () => {
  it('renders the public login route', async () => {
    const wrapper = await mountAt('/app/login')

    expect(wrapper.text()).toContain('登录')
  })

  it('renders the user home shell', async () => {
    const wrapper = await mountAt('/app')

    expect(wrapper.text()).toContain('P1 基础闭环')
  })
})

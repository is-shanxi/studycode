import { afterEach } from 'vitest'

afterEach(() => {
  document.body.innerHTML = ''
  document.cookie = 'XSRF-TOKEN=; Max-Age=0; path=/'
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authApi } from '@/api'
import { useUserStore } from './user'

vi.mock('@/api', () => ({
  authApi: { me: vi.fn() },
}))

function memoryStorage() {
  const values = new Map()
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key),
    clear: () => values.clear(),
  }
}

describe('user store', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('sessionStorage', memoryStorage())
    vi.stubGlobal('localStorage', memoryStorage())
    setActivePinia(createPinia())
  })

  it('refreshes a cached profile from the authenticated API', async () => {
    sessionStorage.setItem('token', 'test-token')
    sessionStorage.setItem('user', JSON.stringify({ username: 'admin', real_name: 'old-name' }))
    authApi.me.mockResolvedValue({ username: 'admin', role: 'admin', real_name: '系统管理员' })

    const store = useUserStore()
    await store.refreshCurrentUser()

    expect(authApi.me).toHaveBeenCalledWith({ suppressError: true })
    expect(store.user.real_name).toBe('系统管理员')
    expect(JSON.parse(sessionStorage.getItem('user')).real_name).toBe('系统管理员')
  })

  it('does not request a profile when there is no token', async () => {
    const store = useUserStore()

    await expect(store.refreshCurrentUser()).resolves.toBeNull()
    expect(authApi.me).not.toHaveBeenCalled()
  })
})

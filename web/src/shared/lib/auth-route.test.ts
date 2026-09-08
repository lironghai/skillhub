import { describe, expect, it, vi } from 'vitest'
import { isRedirect } from '@tanstack/react-router'
import { buildReturnTo, createRequireAuth } from './auth-route'

describe('auth-route', () => {
  it('buildReturnTo preserves pathname search and hash', () => {
    expect(buildReturnTo({
      pathname: '/space/global/caldav-calendar',
      searchStr: '?tab=files',
      hash: '#readme',
    })).toBe('/space/global/caldav-calendar?tab=files#readme')
  })

  it('createRequireAuth redirects unauthenticated users to login with returnTo', async () => {
    const requireAuth = createRequireAuth(async () => null)

    await expect(requireAuth({
      location: {
        pathname: '/space/global/caldav-calendar',
        searchStr: '?tab=files',
        hash: '#readme',
      },
    })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) {
        return false
      }
      expect(error.options.to).toBe('/login')
      expect(error.options.search).toEqual({
        returnTo: '/space/global/caldav-calendar?tab=files#readme',
      })
      return true
    })
  })

  it('createRequireAuth redirects when the current-user probe fails', async () => {
    const requireAuth = createRequireAuth(async () => {
      throw new Error('HTTP 500')
    })

    await expect(requireAuth({
      location: {
        pathname: '/dashboard/mcp',
      },
    })).rejects.toSatisfy((error: unknown) => {
      expect(isRedirect(error)).toBe(true)
      if (!isRedirect(error)) {
        return false
      }
      expect(error.options.to).toBe('/login')
      expect(error.options.search).toEqual({
        returnTo: '/dashboard/mcp',
      })
      return true
    })
  })

  it('createRequireAuth returns the current user when authenticated', async () => {
    const user = { userId: 'user-1' }
    const getCurrentUser = vi.fn(async () => user)
    const requireAuth = createRequireAuth(getCurrentUser)

    await expect(requireAuth({
      location: { pathname: '/dashboard' },
    })).resolves.toEqual({ user })
    expect(getCurrentUser).toHaveBeenCalledTimes(1)
  })
})

/** @vitest-environment jsdom */

import { fireEvent, render, screen } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, className }: { children: ReactNode; to: string; className?: string }) => (
    <a href={to} className={className}>
      {children}
    </a>
  ),
}))

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: () => ({
    setQueryData: vi.fn(),
  }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/api/client', () => ({
  authApi: {
    logout: vi.fn(),
  },
}))

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({ data: [] }),
}))

vi.mock('@/features/review/review-paths', () => ({
  buildGlobalReviewsPath: () => '/dashboard/reviews',
  canAccessReviewCenter: () => false,
}))

vi.mock('@/features/notification/notification-session', () => ({
  clearSessionScopedQueries: vi.fn(),
}))

vi.mock('@/shared/lib/governance-access', () => ({
  canViewGovernanceCenter: () => false,
}))

import * as mod from './user-menu'

/**
 * UserMenu is a React component that renders a hover/click dropdown menu with
 * role-based navigation links (dashboard, reviews, admin, etc.) and logout.
 * Internal helpers (hasRole, closeMenu, handleMouseEnter/Leave) and the
 * menuItemClassName constant are scoped inside the component function.
 * There are no exported pure helpers or constants to test here.
 *
 * We verify the module shape so downstream consumers break fast
 * if the export contract changes.
 */
describe('user-menu module exports', () => {
  it('exports the UserMenu component', () => {
    expect(mod.UserMenu).toBeTypeOf('function')
  })

  it('renders an MCP management menu link when the menu is opened', () => {
    render(
      <mod.UserMenu
        user={{
          displayName: 'Admin User',
          platformRoles: ['SUPER_ADMIN'],
        }}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Admin User' }))

    const mcpLink = screen.getByRole('link', { name: 'user.menu.mcpManagement' })
    expect(mcpLink.getAttribute('href')).toBe('/dashboard/mcp')
  })
})

// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const useSearchMock = vi.fn()
const useSearchSkillBundlesMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useSearch: () => useSearchMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (options && typeof options.count === 'number') {
          return `${key}:${options.count}`
        }
        return key
      },
    }),
  }
})

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: () => ({
    data: [
      { slug: 'analysis', type: 'RECOMMENDED', displayName: 'Analysis' },
      { slug: 'hidden', type: 'PRIVILEGED', displayName: 'Hidden' },
    ],
  }),
}))

vi.mock('@/shared/hooks/use-skill-bundle-queries', () => ({
  useSearchSkillBundles: (params: Record<string, unknown>) => useSearchSkillBundlesMock(params),
}))

vi.mock('@/app/page-shell-style', () => ({
  APP_SHELL_PAGE_CLASS_NAME: 'page-shell',
}))

import { SkillBundlesPage } from './skill-bundles'

describe('SkillBundlesPage', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    navigateMock.mockReset()
    useSearchMock.mockReturnValue({
      q: 'agent',
      namespace: 'team-ai',
      label: 'analysis',
      sort: 'newest',
      page: 0,
    })
    useSearchSkillBundlesMock.mockReturnValue({
      data: {
        items: [{
          id: 1,
          namespace: 'team-ai',
          slug: 'ops-expert',
          name: 'Ops Expert Bundle',
          summary: 'Bundle summary',
          avatarUrl: 'https://cdn.example.com/ops-expert.png',
          roleDescription: 'Role summary',
          applicableScenarios: 'Scenario',
          visibility: 'PUBLIC',
          status: 'PUBLISHED',
          skillCount: 2,
          labels: [{ slug: 'page-only', type: 'RECOMMENDED', displayName: 'Page Only' }],
          updatedAt: '2026-07-28T00:00:00Z',
        }],
        total: 1,
        page: 0,
        size: 12,
      },
      isLoading: false,
      isFetching: false,
    })
  })

  it('renders skill type filters from label metadata and excludes privileged labels', () => {
    render(<SkillBundlesPage />)

    const avatar = screen.getByAltText('Ops Expert Bundle')
    expect(avatar.getAttribute('src')).toBe('https://cdn.example.com/ops-expert.png')
    expect(screen.getByRole('button', { name: 'Analysis' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Hidden' })).toBeNull()
    expect(screen.queryByRole('button', { name: 'Page Only' })).toBeNull()
  })

  it('passes URL search state to the bundle search hook and opens bundle detail', () => {
    render(<SkillBundlesPage />)

    const namespaceBadge = screen.getByText('@team-ai')
    expect(namespaceBadge.className).toContain('text-accent')
    expect(namespaceBadge.className).not.toContain('handle-tag')

    expect(useSearchSkillBundlesMock).toHaveBeenCalledWith(expect.objectContaining({
      q: 'agent',
      namespace: 'team-ai',
      label: 'analysis',
      sort: 'newest',
      page: 0,
      size: 12,
    }))

    fireEvent.click(screen.getByText('Ops Expert Bundle'))

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/skill-bundles/team-ai/ops-expert',
    })
  })

  it('shows a persistent create action for expert packages', () => {
    render(<SkillBundlesPage />)

    fireEvent.click(screen.getByRole('button', { name: 'skillBundles.createAction' }))

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/dashboard/skill-bundles',
    })
  })
})

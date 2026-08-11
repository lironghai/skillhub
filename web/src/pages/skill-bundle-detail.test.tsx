// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const useParamsMock = vi.fn()
const useSkillBundleDetailMock = vi.fn()

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => useParamsMock(),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string, options?: Record<string, unknown>) => {
        if (key === 'skillBundleDetail.skillTotalValue') {
          return `${options?.skillCount} skills`
        }
        return options?.defaultValue as string || key
      },
    }),
  }
})

vi.mock('@/shared/hooks/use-skill-bundle-queries', () => ({
  useSkillBundleDetail: () => useSkillBundleDetailMock(),
}))

vi.mock('@/app/page-shell-style', () => ({
  APP_SHELL_PAGE_CLASS_NAME: 'page-shell',
}))

import { SkillBundleDetailPage } from './skill-bundle-detail'

describe('SkillBundleDetailPage', () => {
  beforeEach(() => {
    navigateMock.mockReset()
    useParamsMock.mockReturnValue({ namespace: 'team-ai', slug: 'ops-expert' })
    useSkillBundleDetailMock.mockReturnValue({
      data: {
        id: 1,
        namespace: 'team-ai',
        slug: 'ops-expert',
        name: 'Ops Expert Bundle',
        summary: 'Bundle summary',
        avatarUrl: 'https://cdn.example.com/ops-expert.png',
        description: 'Introduction',
        roleDescription: 'Role',
        applicableScenarios: 'Scenarios',
        methodology: 'Methodology',
        recommendedSkillNotes: 'Recommended notes',
        visibility: 'PUBLIC',
        status: 'PUBLISHED',
        canManage: true,
        skillCount: 1,
        downloadCount: 7,
        labels: [{ slug: 'analysis', type: 'RECOMMENDED', displayName: 'Analysis' }],
        items: [{
          skillId: 10,
          namespace: 'team-ai',
          skillSlug: 'triage',
          displayName: 'Triage Skill',
          sortOrder: 0,
          note: 'Use first',
        }],
        createdAt: '2026-07-28T00:00:00Z',
        updatedAt: '2026-07-28T00:00:00Z',
      },
      isLoading: false,
      isError: false,
    })
  })

  afterEach(() => {
    cleanup()
  })

  it('renders expert package fields and opens included skills', () => {
    render(<SkillBundleDetailPage />)

    expect(screen.getByText('Ops Expert Bundle')).toBeTruthy()
    const avatar = screen.getByAltText('Ops Expert Bundle')
    expect(avatar.getAttribute('src')).toBe('https://cdn.example.com/ops-expert.png')
    expect(screen.getByText('skillBundleDetail.capabilityIntro')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.capabilityCanDo')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.workingStyle')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.expertise')).toBeTruthy()
    expect(screen.getByText('1 skills')).toBeTruthy()
    expect(screen.getByText('PUBLISHED').className).toContain('badge-soft-green')
    expect(screen.getByText('PUBLISHED').className).not.toContain('status-pill')
    expect(screen.getByText('PUBLIC').className).toContain('badge-soft-green')
    expect(screen.getByText('PUBLIC').className).not.toContain('status-pill')
    expect(screen.getByText('Introduction')).toBeTruthy()
    expect(screen.getByText('Role')).toBeTruthy()
    expect(screen.getByText('Scenarios')).toBeTruthy()
    expect(screen.getByText('Methodology')).toBeTruthy()
    expect(screen.getByText('Recommended notes')).toBeTruthy()
    expect(screen.getAllByText('Analysis').length).toBe(2)
    expect(screen.getByText('skillBundleDetail.fileBrowser')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.packageInfo')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.downloads')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.rating')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.ratingNone')).toBeTruthy()
    expect(screen.getAllByText('triage').length).toBeGreaterThan(0)
    expect(screen.getByText('SKILL.md')).toBeTruthy()
    expect(screen.getByText('skillBundleDetail.tags')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'skillBundleDetail.edit' })).toBeTruthy()
    const namespaceBadge = screen.getAllByText('@team-ai')[0]
    expect(namespaceBadge.className).toContain('text-accent')
    expect(namespaceBadge.className).not.toContain('handle-tag')
    expect(screen.getByRole('link', { name: 'skillBundleDetail.download' }).getAttribute('href'))
      .toBe('/api/web/skill-bundles/team-ai/ops-expert/download')

    fireEvent.click(screen.getByText('Triage Skill'))

    expect(navigateMock).toHaveBeenCalledWith({
      to: '/space/team-ai/triage',
    })
  })

  it('hides edit action when the current viewer cannot manage the expert package', () => {
    useSkillBundleDetailMock.mockReturnValue({
      data: {
        id: 1,
        namespace: 'team-ai',
        slug: 'ops-expert',
        name: 'Ops Expert Bundle',
        summary: 'Bundle summary',
        avatarUrl: null,
        description: null,
        roleDescription: null,
        applicableScenarios: null,
        methodology: null,
        recommendedSkillNotes: null,
        visibility: 'PUBLIC',
        status: 'PUBLISHED',
        canManage: false,
        skillCount: 0,
        labels: [],
        items: [],
        createdAt: '2026-07-28T00:00:00Z',
        updatedAt: '2026-07-28T00:00:00Z',
      },
      isLoading: false,
      isError: false,
    })

    render(<SkillBundleDetailPage />)

    expect(screen.getByRole('link', { name: 'skillBundleDetail.download' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'skillBundleDetail.edit' })).toBeNull()
  })
})

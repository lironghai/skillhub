// @vitest-environment jsdom

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const navigateMock = vi.fn()
const toastMocks = vi.hoisted(() => ({
  success: vi.fn(),
  error: vi.fn(),
}))
const createMutationMock = vi.hoisted(() => vi.fn())
const updateMutationMock = vi.hoisted(() => vi.fn())
const detailMock = vi.hoisted(() => vi.fn())

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigateMock,
  useParams: () => ({ namespace: 'team-ai', slug: 'ops-expert' }),
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

vi.mock('@/shared/hooks/use-namespace-queries', () => ({
  useMyNamespaces: () => ({
    data: [{ id: 1, slug: 'team-ai', displayName: 'Team AI', type: 'TEAM' }],
    isLoading: false,
  }),
}))

vi.mock('@/shared/hooks/use-label-queries', () => ({
  useVisibleLabels: () => ({
    data: [{ slug: 'analysis', type: 'RECOMMENDED', displayName: 'Analysis' }],
  }),
}))

vi.mock('@/shared/hooks/use-skill-queries', () => ({
  useSearchSkills: () => ({
    data: { items: [], total: 0, page: 0, size: 6 },
  }),
}))

vi.mock('@/shared/hooks/use-skill-bundle-queries', () => ({
  useSkillBundleDetail: () => detailMock(),
  useCreateSkillBundle: () => ({ mutateAsync: createMutationMock, isPending: false }),
  useUpdateSkillBundle: () => ({ mutateAsync: updateMutationMock, isPending: false }),
}))

vi.mock('@/shared/lib/toast', () => ({
  toast: {
    success: toastMocks.success,
    error: toastMocks.error,
  },
}))

import { CreateSkillBundlePage, EditSkillBundlePage } from './skill-bundles'

describe('CreateSkillBundlePage', () => {
  afterEach(() => {
    cleanup()
  })

  beforeEach(() => {
    navigateMock.mockReset()
    toastMocks.error.mockReset()
    createMutationMock.mockReset()
    updateMutationMock.mockReset()
    detailMock.mockReturnValue({ data: undefined, isLoading: false })
  })

  it('validates required bundle fields before saving', () => {
    render(<CreateSkillBundlePage />)

    fireEvent.click(screen.getByText('skillBundleEditor.save'))

    expect(toastMocks.error).toHaveBeenCalledWith(
      'skillBundleEditor.validationTitle',
      'skillBundleEditor.requiredFields',
    )
  })

  it('lets maintainers set an expert avatar and includes it in the save request', async () => {
    updateMutationMock.mockResolvedValue({
      namespace: 'team-ai',
      slug: 'ops-expert',
      name: 'Ops Expert',
    })
    detailMock.mockReturnValue({
      data: {
        namespace: 'team-ai',
        name: 'Ops Expert',
        slug: 'ops-expert',
        summary: '',
        avatarUrl: '',
        description: '',
        roleDescription: '',
        applicableScenarios: '',
        methodology: '',
        recommendedSkillNotes: '',
        visibility: 'PRIVATE',
        status: 'DRAFT',
        labels: [],
        items: [],
      },
      isLoading: false,
    })

    render(<EditSkillBundlePage />)

    fireEvent.change(screen.getByLabelText('skillBundleEditor.avatarUrl'), {
      target: { value: 'https://cdn.example.com/ops-expert.png' },
    })
    fireEvent.click(screen.getByText('skillBundleEditor.save'))

    expect(await screen.findByAltText('skillBundleEditor.avatarPreviewAlt')).toBeTruthy()
    expect(updateMutationMock).toHaveBeenCalledWith(expect.objectContaining({
      avatarUrl: 'https://cdn.example.com/ops-expert.png',
    }))
  })

  it('does not show the expert package name as a save success toast description', async () => {
    updateMutationMock.mockResolvedValue({
      namespace: 'team-ai',
      slug: 'ops-expert',
      name: 'Staging Expert Bundle',
    })
    detailMock.mockReturnValue({
      data: {
        namespace: 'team-ai',
        name: 'Staging Expert Bundle',
        slug: 'ops-expert',
        summary: '',
        avatarUrl: '',
        description: '',
        roleDescription: '',
        applicableScenarios: '',
        methodology: '',
        recommendedSkillNotes: '',
        visibility: 'PRIVATE',
        status: 'DRAFT',
        labels: [],
        items: [],
      },
      isLoading: false,
    })

    render(<EditSkillBundlePage />)

    fireEvent.click(screen.getByText('skillBundleEditor.save'))

    expect(updateMutationMock).toHaveBeenCalled()
    expect(toastMocks.success).toHaveBeenCalledWith('skillBundleEditor.updateSuccessTitle')
  })
})

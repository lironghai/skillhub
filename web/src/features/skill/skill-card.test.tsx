import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'
import { SkillCard } from './skill-card'
import type { SkillSummary } from '@/api/types'

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))

vi.mock('@/features/social/use-star', () => ({
  useStar: () => ({ data: { starred: false } }),
}))

const baseSkill: SkillSummary = {
  id: 1,
  slug: 'demo',
  displayName: 'Demo Skill',
  summary: 'A useful skill',
  downloadCount: 12,
  starCount: 3,
  ratingCount: 0,
  namespace: 'team-ai',
  updatedAt: '2026-03-20T00:00:00Z',
  canSubmitPromotion: false,
}

function renderCard(skill: Partial<SkillSummary> = {}) {
  return renderToStaticMarkup(<SkillCard skill={{ ...baseSkill, ...skill }} />)
}

describe('SkillCard', () => {
  it('renders only recommended label chips, caps them at two, and folds the remaining recommended count', () => {
    const html = renderCard({
      labels: [
        { slug: 'code-generation', type: 'RECOMMENDED', displayName: 'Code Generation' },
        { slug: 'official', type: 'RECOMMENDED', displayName: 'Official' },
        { slug: 'automation', type: 'RECOMMENDED', displayName: 'Automation' },
        { slug: 'private-review', type: 'PRIVILEGED', displayName: 'Private Review' },
      ],
    })

    expect(html).toContain('Code Generation')
    expect(html).toContain('Official')
    expect(html).toContain('+1')
    expect(html).not.toContain('Automation')
    expect(html).not.toContain('Private Review')
  })

  it('keeps the label chip row stable when a skill has no labels', () => {
    const html = renderCard({ labels: [] })

    expect(html).toContain('min-h-6')
    expect(html).not.toContain('+')
  })
})

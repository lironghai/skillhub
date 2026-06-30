import { renderToStaticMarkup } from 'react-dom/server'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
    }),
  }
})

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title, subtitle, actions }: { title: string; subtitle: string; actions?: ReactNode }) => (
    <header>
      <h1>{title}</h1>
      <p>{subtitle}</p>
      {actions}
    </header>
  ),
}))

vi.mock('@/shared/components/pagination', () => ({
  Pagination: ({ page, totalPages }: { page: number; totalPages: number }) => (
    <nav>{`page ${page + 1} of ${totalPages}`}</nav>
  ),
}))

vi.mock('./use-mcp-catalog', () => ({
  useMcpInternalServers: () => ({
    data: {
      items: [
        {
          id: '34eaa0d257da49608da2c6b079ed0b5',
          name: 'bdc4_group',
          description: 'bdc4_group',
          enabled: true,
          visibility: 'public',
          ownerEmail: 'admin@mcp-context-forge.yingxiong.com',
          team: 'Platform Admin',
          toolCount: 5,
          resourceCount: 11,
          promptCount: 3,
          tags: ['bdc4'],
          streamableHttpUrl: 'https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp',
          sseUrl: 'https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse',
        },
      ],
      total: 1,
      page: 0,
      size: 24,
    },
    isLoading: false,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
  useMcpCatalog: () => ({
    data: {
      items: [
        {
          id: 'github',
          name: 'GitHub',
          category: 'Software Development',
          provider: 'GitHub',
          description: 'Version control and collaborative software development',
          url: 'https://api.githubcopilot.com/mcp',
          authType: 'OAuth2.1',
          requiresApiKey: false,
          secure: true,
          tags: ['development', 'git'],
          transport: 'STREAMABLEHTTP',
          logoUrl: null,
          documentationUrl: 'https://docs.github.com',
          registered: true,
          available: true,
          requiresOauthConfig: false,
        },
      ],
      total: 1,
      page: 0,
      size: 24,
      categories: ['Software Development'],
      authTypes: ['OAuth2.1'],
      providers: ['GitHub'],
      tags: ['development', 'git'],
    },
    isLoading: false,
    isFetching: false,
    isError: false,
    refetch: vi.fn(),
  }),
}))

import { McpMarketplacePage } from './mcp-marketplace-page'

describe('McpMarketplacePage', () => {
  it('renders internal ContextForge virtual server cards with full connection URLs', () => {
    const html = renderToStaticMarkup(<McpMarketplacePage />)

    expect(html).toContain('mcpMarketplace.title')
    expect(html).toContain('mcpMarketplace.internalSearchPlaceholder')
    expect(html).toContain('mcpMarketplace.internalTab')
    expect(html).toContain('mcpMarketplace.openSourceTab')
    expect(html).toContain('bdc4_group')
    expect(html).toContain('34eaa0d257da49608da2c6b079ed0b5')
    expect(html).toContain('https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp')
    expect(html).toContain('https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse')
    expect(html).toContain('bdc4')
    expect(html).not.toContain('<iframe')
  })
})

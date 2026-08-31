/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { renderToStaticMarkup } from 'react-dom/server'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'

const mcpCatalogHooks = vi.hoisted(() => {
  const internalRefetch = vi.fn()
  const catalogRefetch = vi.fn()
  return {
    useMcpInternalServers: vi.fn(() => ({
      data: {
        items: [
          {
            id: '34eaa0d257da49608da2c6b079ed0b5',
            name: 'bdc4_group',
            description: 'bdc4_group',
            enabled: true,
            visibility: 'public',
            ownerEmail: 'admi',
            team: 'Platform Admin',
            toolCount: 5,
            resourceCount: 11,
            promptCount: 3,
            tools: [
              {
                id: 'tool-a',
                name: 'query_report_by_code',
                description: 'Query BDC report data by code',
              },
            ],
            resources: [
              {
                id: 'resource-a',
                name: 'bdc_schema',
                description: 'BDC schema resource',
              },
            ],
            prompts: [
              {
                id: 'prompt-a',
                name: 'bdc_router',
                description: 'BDC router prompt',
              },
            ],
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
      refetch: internalRefetch,
    })),
    useMcpCatalog: vi.fn(() => ({
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
      refetch: catalogRefetch,
    })),
  }
})

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
  useMcpInternalServers: mcpCatalogHooks.useMcpInternalServers,
  useMcpCatalog: mcpCatalogHooks.useMcpCatalog,
}))

import { AssociatedDetailList, McpMarketplacePage } from './mcp-marketplace-page'

describe('McpMarketplacePage', () => {
  afterEach(() => {
    cleanup()
  })

  it('renders compact internal server rows with detail actions instead of inline associated items', () => {
    mcpCatalogHooks.useMcpInternalServers.mockClear()
    mcpCatalogHooks.useMcpCatalog.mockClear()

    const html = renderToStaticMarkup(<McpMarketplacePage />)

    expect(mcpCatalogHooks.useMcpInternalServers).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 24 }), { enabled: true })
    expect(mcpCatalogHooks.useMcpCatalog).toHaveBeenCalledWith(expect.objectContaining({ page: 0, size: 24 }), { enabled: false })
    expect(html).toContain('mcpMarketplace.title')
    expect(html).toContain('mcpMarketplace.internalSearchPlaceholder')
    expect(html).toContain('mcpMarketplace.internalTab')
    expect(html).toContain('mcpMarketplace.openSourceTab')
    expect(html).toContain('bdc4_group')
    expect(html).toContain('mcpMarketplace.toolsCount')
    expect(html).toContain('mcpMarketplace.resourcesCount')
    expect(html).toContain('mcpMarketplace.promptsCount')
    expect(html).toContain('button')
    expect(html).not.toContain('query_report_by_code')
    expect(html).not.toContain('Query BDC report data by code')
    expect(html).not.toContain('bdc_schema')
    expect(html).not.toContain('bdc_router')
    expect(html).not.toContain('/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp')
    expect(html).not.toContain('/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse')
    expect(html).toContain('<table')
    expect(html).not.toContain('<iframe')
  })

  it('renders view toggle buttons on the internal tab', () => {
    mcpCatalogHooks.useMcpInternalServers.mockClear()
    mcpCatalogHooks.useMcpCatalog.mockClear()

    const html = renderToStaticMarkup(<McpMarketplacePage />)

    // View toggle should be present on internal tab
    expect(html).toContain('mcpMarketplace.gridView')
    expect(html).toContain('mcpMarketplace.listView')
    expect(html).toContain('mcpMarketplace.tabsLabel')
    // Default is list view, so the table should be visible.
    expect(html).toContain('<table')
    expect(html).not.toContain('mcpMarketplace.serverName')
  })

  it('shows the internal MCP connection URLs when a server row is opened', () => {
    render(<McpMarketplacePage />)

    fireEvent.click(screen.getByRole('row', { name: /bdc4_group/ }))

    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByText('mcpMarketplace.streamableHttpUrl')).toBeTruthy()
    expect(screen.getByText('https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/mcp')).toBeTruthy()
    expect(screen.getByText('mcpMarketplace.sseUrl')).toBeTruthy()
    expect(screen.getByText('https://skillhub.example/contextforge/servers/34eaa0d257da49608da2c6b079ed0b5/sse')).toBeTruthy()
  })

  it('renders associated item details inside the detail list', () => {
    const html = renderToStaticMarkup(
      <AssociatedDetailList
        emptyLabel="No tools"
        unnamedLabel="Unnamed tool"
        items={[
          {
            id: 'tool-a',
            name: 'query_report_by_code',
            description: 'Query BDC report data by code',
          },
        ]}
      />,
    )

    expect(html).toContain('query_report_by_code')
    expect(html).toContain('Query BDC report data by code')
    expect(html).not.toContain('No tools')
  })
})

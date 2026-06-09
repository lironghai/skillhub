import { renderToStaticMarkup } from 'react-dom/server'
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
  DashboardPageHeader: ({ title, subtitle }: { title: string; subtitle: string }) => (
    <header>
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </header>
  ),
}))

vi.mock('@/app/page-shell-style', () => ({
  APP_SHELL_PAGE_CLASS_NAME: 'page-shell',
}))

import { McpManagementPage } from './mcp-management'

describe('McpManagementPage', () => {
  it('renders the page title and embedded ContextForge admin iframe', () => {
    const html = renderToStaticMarkup(<McpManagementPage />)

    expect(html).toContain('mcpManagement.title')
    expect(html).toContain('mcpManagement.subtitle')
    expect(html).toContain('mcpManagement.loginHint')
    expect(html).toContain('href="/contextforge/admin/login"')
    expect(html).toContain('mcpManagement.openInNewTab')
    expect(html).toContain('src="/contextforge/admin/login"')
    expect(html).toContain('title="mcpManagement.iframeTitle"')
  })
})

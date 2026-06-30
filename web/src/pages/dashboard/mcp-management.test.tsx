import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/features/mcp/mcp-marketplace-page', () => ({
  McpMarketplacePage: () => <main>MCP marketplace page</main>,
}))

import { McpManagementPage } from './mcp-management'

describe('McpManagementPage', () => {
  it('renders the MCP marketplace page instead of embedding ContextForge admin', () => {
    const html = renderToStaticMarkup(<McpManagementPage />)

    expect(html).toContain('MCP marketplace page')
    expect(html).not.toContain('<iframe')
  })
})

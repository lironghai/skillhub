import { describe, expect, it } from 'vitest'
import { buildMcpCatalogUrl, buildMcpInternalServersUrl } from './mcp-catalog-query'

describe('buildMcpCatalogUrl', () => {
  it('keeps the MCP catalog proxy path and serializes filters', () => {
    expect(buildMcpCatalogUrl({
      search: ' git ',
      category: 'Software Development',
      authType: 'OAuth2.1',
      provider: 'GitHub',
      tags: ['development', ' git ', ''],
      page: 2,
      size: 24,
    })).toBe('/api/web/mcp/servers?page=2&size=24&search=git&category=Software+Development&auth_type=OAuth2.1&provider=GitHub&tags=development&tags=git')
  })

  it('keeps the internal ContextForge virtual server proxy path', () => {
    expect(buildMcpInternalServersUrl({
      search: ' bdc4 ',
      page: 1,
      size: 12,
    })).toBe('/api/web/mcp/internal-servers?page=1&size=12&search=bdc4')
  })
})

import { WEB_API_PREFIX } from '@/api/client'
import type { McpCatalogQuery } from './mcp-catalog-types'

export const MCP_CATALOG_PAGE_SIZE = 24

export function buildMcpCatalogUrl(query: McpCatalogQuery = {}): string {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? MCP_CATALOG_PAGE_SIZE))
  appendParam(params, 'search', query.search)
  appendParam(params, 'category', query.category)
  appendParam(params, 'auth_type', query.authType)
  appendParam(params, 'provider', query.provider)

  for (const tag of query.tags ?? []) {
    appendParam(params, 'tags', tag)
  }

  return `${WEB_API_PREFIX}/mcp/servers?${params.toString()}`
}

export function buildMcpInternalServersUrl(query: Pick<McpCatalogQuery, 'search' | 'page' | 'size'> = {}): string {
  const params = new URLSearchParams()
  params.set('page', String(query.page ?? 0))
  params.set('size', String(query.size ?? MCP_CATALOG_PAGE_SIZE))
  appendParam(params, 'search', query.search)
  return `${WEB_API_PREFIX}/mcp/internal-servers?${params.toString()}`
}

function appendParam(params: URLSearchParams, name: string, value?: string) {
  const normalized = value?.trim()
  if (normalized) {
    params.append(name, normalized)
  }
}

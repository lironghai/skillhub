import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'
import type { McpCatalogQuery, McpCatalogResponse, McpInternalServerResponse } from './mcp-catalog-types'
import { buildMcpCatalogUrl, buildMcpInternalServersUrl } from './mcp-catalog-query'

type McpCatalogQueryOptions = {
  enabled?: boolean
}

export function useMcpCatalog(query: McpCatalogQuery, options: McpCatalogQueryOptions = {}) {
  return useQuery({
    queryKey: ['mcp-catalog', query],
    queryFn: () => fetchJson<McpCatalogResponse>(buildMcpCatalogUrl(query)),
    enabled: options.enabled ?? true,
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  })
}

export function useMcpInternalServers(query: Pick<McpCatalogQuery, 'search' | 'page' | 'size'>, options: McpCatalogQueryOptions = {}) {
  return useQuery({
    queryKey: ['mcp-internal-servers', query],
    queryFn: () => fetchJson<McpInternalServerResponse>(buildMcpInternalServersUrl(query)),
    enabled: options.enabled ?? true,
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  })
}

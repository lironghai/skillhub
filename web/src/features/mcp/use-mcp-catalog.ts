import { useQuery } from '@tanstack/react-query'
import { fetchJson } from '@/api/client'
import type { McpCatalogQuery, McpCatalogResponse, McpInternalServerResponse } from './mcp-catalog-types'
import { buildMcpCatalogUrl, buildMcpInternalServersUrl } from './mcp-catalog-query'

export function useMcpCatalog(query: McpCatalogQuery) {
  return useQuery({
    queryKey: ['mcp-catalog', query],
    queryFn: () => fetchJson<McpCatalogResponse>(buildMcpCatalogUrl(query)),
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  })
}

export function useMcpInternalServers(query: Pick<McpCatalogQuery, 'search' | 'page' | 'size'>) {
  return useQuery({
    queryKey: ['mcp-internal-servers', query],
    queryFn: () => fetchJson<McpInternalServerResponse>(buildMcpInternalServersUrl(query)),
    staleTime: 0,
    gcTime: 0,
    refetchOnMount: 'always',
  })
}

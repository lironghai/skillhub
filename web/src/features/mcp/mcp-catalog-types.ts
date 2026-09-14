export type McpAssociatedItem = {
  id?: string
  name?: string
  description?: string
  inputSchema?: unknown
  outputSchema?: unknown
}

export type McpCatalogItem = {
  id: string
  name: string
  category?: string
  provider?: string
  description?: string
  url?: string
  authType?: string
  requiresApiKey: boolean
  secure: boolean
  tags: string[]
  transport: string
  logoUrl: string
  documentationUrl: string
  registered: boolean
  available: boolean
  requiresOauthConfig: boolean
}

export type McpInternalServerItem = {
  id: string
  name: string
  description?: string
  iconUrl?: string
  enabled: boolean
  visibility: string
  team: string
  toolCount: number
  resourceCount: number
  promptCount: number
  tags: string[]
  streamableHttpUrl: string
  sseUrl: string
  tools: McpAssociatedItem[]
  resources: McpAssociatedItem[]
  prompts: McpAssociatedItem[]
}
export type McpCatalogResponse = {
  items: McpCatalogItem[]
  total: number
  page: number
  size: number
  categories?: string[]
  authTypes?: string[]
  providers?: string[]
  tags?: string[]
}
export type McpInternalServerResponse = {
  total: number
  page: number
  size: number
  items: McpInternalServerItem[]
}

export interface McpCatalogQuery {
  search?: string
  category?: string
  authType?: string
  provider?: string
  tags?: string[]
  page?: number
  size?: number
}

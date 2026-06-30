export interface McpCatalogItem {
  id: string
  name: string
  category?: string | null
  provider?: string | null
  description?: string | null
  url?: string | null
  authType?: string | null
  requiresApiKey: boolean
  secure: boolean
  tags: string[]
  transport?: string | null
  logoUrl?: string | null
  documentationUrl?: string | null
  registered: boolean
  available: boolean
  requiresOauthConfig: boolean
}

export interface McpCatalogResponse {
  items: McpCatalogItem[]
  total: number
  page: number
  size: number
  categories: string[]
  authTypes: string[]
  providers: string[]
  tags: string[]
}

export interface McpAssociatedItem {
  id?: string | null
  name?: string | null
  description?: string | null
}

export interface McpInternalServerItem {
  id: string
  name: string
  description?: string | null
  enabled: boolean
  visibility?: string | null
  ownerEmail?: string | null
  team?: string | null
  toolCount: number
  resourceCount: number
  promptCount: number
  tools: McpAssociatedItem[]
  resources: McpAssociatedItem[]
  prompts: McpAssociatedItem[]
  tags: string[]
  streamableHttpUrl?: string | null
  sseUrl?: string | null
}

export interface McpInternalServerResponse {
  items: McpInternalServerItem[]
  total: number
  page: number
  size: number
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

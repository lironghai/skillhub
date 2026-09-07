import type { components } from '@/api/generated/schema'

type Schemas = components['schemas']

export type McpCatalogItem = Required<Schemas['McpCatalogItemResponse']>
export type McpAssociatedItem = Schemas['McpAssociatedItemResponse']
export type McpInternalServerItem = Omit<Required<Schemas['McpInternalServerItemResponse']>, 'tools' | 'resources' | 'prompts'> & {
  tools: McpAssociatedItem[]
  resources: McpAssociatedItem[]
  prompts: McpAssociatedItem[]
}
export type McpCatalogResponse = Omit<Required<Schemas['McpCatalogResponse']>, 'items'> & { items: McpCatalogItem[] }
export type McpInternalServerResponse = Omit<Required<Schemas['McpInternalServerResponse']>, 'items'> & {
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

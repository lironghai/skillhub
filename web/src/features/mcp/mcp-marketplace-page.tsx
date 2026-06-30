import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, Copy, ExternalLink, KeyRound, Loader2, RefreshCw, Search, Server, ShieldCheck, X } from 'lucide-react'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Input } from '@/shared/ui/input'
import { cn } from '@/shared/lib/utils'
import { MAX_SEARCH_QUERY_LENGTH } from '@/shared/lib/search-query'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { MCP_CATALOG_PAGE_SIZE } from './mcp-catalog-query'
import type { McpCatalogItem, McpInternalServerItem } from './mcp-catalog-types'
import { useMcpCatalog, useMcpInternalServers } from './use-mcp-catalog'

type McpMarketplaceTab = 'internal' | 'opensource'

export function McpMarketplacePage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<McpMarketplaceTab>('internal')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const internalQuery = useMcpInternalServers({
    search,
    page,
    size: MCP_CATALOG_PAGE_SIZE,
  })
  const openSourceQuery = useMcpCatalog({
    search,
    page,
    size: MCP_CATALOG_PAGE_SIZE,
  })
  const activeQuery = activeTab === 'internal' ? internalQuery : openSourceQuery
  const totalPages = useMemo(() => {
    const data = activeQuery.data
    if (!data || data.size <= 0) {
      return 1
    }
    return Math.max(Math.ceil(data.total / data.size), 1)
  }, [activeQuery.data])

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    setPage(0)
    setSearch(searchInput.trim())
  }

  const handleClear = () => {
    setSearchInput('')
    setSearch('')
    setPage(0)
  }

  const handleTabChange = (tab: McpMarketplaceTab) => {
    setActiveTab(tab)
    setPage(0)
  }

  return (
    <div className="space-y-6 animate-fade-up">
      <DashboardPageHeader
        title={t('mcpMarketplace.title')}
        subtitle={t('mcpMarketplace.subtitle')}
        actions={(
          <Button type="button" variant="outline" onClick={() => activeQuery.refetch()} disabled={activeQuery.isFetching}>
            {activeQuery.isFetching ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" />
            ) : (
              <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
            )}
            {t('mcpMarketplace.refresh')}
          </Button>
        )}
      />

      <div className="inline-flex rounded-lg border bg-card p-1 shadow-sm" role="tablist" aria-label={t('mcpMarketplace.tabsLabel')}>
        <TabButton active={activeTab === 'internal'} onClick={() => handleTabChange('internal')}>
          {t('mcpMarketplace.internalTab')}
        </TabButton>
        <TabButton active={activeTab === 'opensource'} onClick={() => handleTabChange('opensource')}>
          {t('mcpMarketplace.openSourceTab')}
        </TabButton>
      </div>

      <form onSubmit={handleSubmit} className="rounded-lg border bg-card p-3 shadow-sm">
        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
            <Input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              maxLength={MAX_SEARCH_QUERY_LENGTH}
              placeholder={activeTab === 'internal' ? t('mcpMarketplace.internalSearchPlaceholder') : t('mcpMarketplace.searchPlaceholder')}
              className="h-11 pl-10 pr-10"
            />
            {searchInput ? (
              <button
                type="button"
                onClick={handleClear}
                className="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                aria-label={t('mcpMarketplace.clearSearch')}
                title={t('mcpMarketplace.clearSearch')}
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            ) : null}
          </div>
          <Button type="submit" className="sm:min-w-28" disabled={activeQuery.isFetching}>
            {activeQuery.isFetching ? <Loader2 className="mr-2 h-4 w-4 animate-spin" aria-hidden="true" /> : <Search className="mr-2 h-4 w-4" aria-hidden="true" />}
            {t('mcpMarketplace.search')}
          </Button>
        </div>
      </form>

      {activeQuery.isError ? (
        <Card className="p-8">
          <EmptyState
            title={t('mcpMarketplace.errorTitle')}
            description={t('mcpMarketplace.errorDescription')}
            action={(
              <Button type="button" variant="outline" onClick={() => activeQuery.refetch()}>
                <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
                {t('mcpMarketplace.retry')}
              </Button>
            )}
          />
        </Card>
      ) : activeQuery.isLoading ? (
        <SkeletonList count={6} />
      ) : activeTab === 'internal' ? (
        <InternalServerList
          servers={(internalQuery.data?.items ?? [])}
          search={search}
          onClear={handleClear}
        />
      ) : (
        <OpenSourceCatalogList
          servers={(openSourceQuery.data?.items ?? [])}
          search={search}
          onClear={handleClear}
        />
      )}

      {!activeQuery.isError && !activeQuery.isLoading && totalPages > 1 ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}
    </div>
  )
}

function TabButton({ active, onClick, children }: { active: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      role="tab"
      aria-selected={active}
      onClick={onClick}
      className={cn(
        'h-9 rounded-md px-4 text-sm font-medium transition-colors',
        active ? 'bg-primary text-primary-foreground shadow-sm' : 'text-muted-foreground hover:bg-secondary hover:text-foreground',
      )}
    >
      {children}
    </button>
  )
}

function InternalServerList({ servers, search, onClear }: { servers: McpInternalServerItem[]; search: string; onClear: () => void }) {
  const { t } = useTranslation()
  if (servers.length === 0) {
    return (
      <Card className="p-8">
        <EmptyState
          title={t('mcpMarketplace.emptyInternalTitle')}
          description={search ? t('mcpMarketplace.emptySearchDescription') : t('mcpMarketplace.emptyInternalDescription')}
          action={search ? <Button type="button" variant="outline" onClick={onClear}>{t('mcpMarketplace.clearSearch')}</Button> : null}
        />
      </Card>
    )
  }
  return (
    <div className="grid grid-cols-1 gap-5 xl:grid-cols-2">
      {servers.map((server) => (
        <InternalServerCard key={server.id} server={server} />
      ))}
    </div>
  )
}

function OpenSourceCatalogList({ servers, search, onClear }: { servers: McpCatalogItem[]; search: string; onClear: () => void }) {
  const { t } = useTranslation()
  if (servers.length === 0) {
    return (
      <Card className="p-8">
        <EmptyState
          title={t('mcpMarketplace.emptyTitle')}
          description={search ? t('mcpMarketplace.emptySearchDescription') : t('mcpMarketplace.emptyDescription')}
          action={search ? <Button type="button" variant="outline" onClick={onClear}>{t('mcpMarketplace.clearSearch')}</Button> : null}
        />
      </Card>
    )
  }
  return (
    <div className="grid grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-3">
      {servers.map((server) => (
        <OpenSourceServerCard key={server.id || server.name} server={server} />
      ))}
    </div>
  )
}

function InternalServerCard({ server }: { server: McpInternalServerItem }) {
  const { t } = useTranslation()
  return (
    <Card className="flex min-h-80 flex-col p-5">
      <div className="flex gap-3">
        <div className="flex h-11 w-11 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground">
          <Server className="h-5 w-5" aria-hidden="true" />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="break-words text-base font-semibold leading-6 text-foreground [overflow-wrap:anywhere]">{server.name}</h2>
          <p className="mt-1 break-all text-xs leading-5 text-muted-foreground">{server.id}</p>
        </div>
      </div>

      <p className="mt-4 min-h-12 break-words text-sm leading-6 text-muted-foreground [overflow-wrap:anywhere]">
        {server.description || t('mcpMarketplace.noDescription')}
      </p>

      <div className="mt-4 flex flex-wrap gap-2">
        <StatusBadge active={server.enabled} label={server.enabled ? t('mcpMarketplace.enabled') : t('mcpMarketplace.disabled')} />
        <CountBadge label={t('mcpMarketplace.toolsCount', { count: server.toolCount })} />
        <CountBadge label={t('mcpMarketplace.resourcesCount', { count: server.resourceCount })} />
        <CountBadge label={t('mcpMarketplace.promptsCount', { count: server.promptCount })} />
      </div>

      {server.tags.length > 0 ? <TagList tags={server.tags} /> : null}

      <div className="mt-5 space-y-3">
        <ConnectionUrl label={t('mcpMarketplace.streamableHttpUrl')} value={server.streamableHttpUrl} />
        <ConnectionUrl label={t('mcpMarketplace.sseUrl')} value={server.sseUrl} />
      </div>

      <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
        <MetaRow label={t('mcpMarketplace.owner')} value={server.ownerEmail} />
        <MetaRow label={t('mcpMarketplace.team')} value={server.team} />
        <MetaRow label={t('mcpMarketplace.visibility')} value={server.visibility} />
      </dl>
    </Card>
  )
}

function OpenSourceServerCard({ server }: { server: McpCatalogItem }) {
  const { t } = useTranslation()
  return (
    <Card className="flex min-h-96 flex-col p-5">
      <div className="flex gap-3">
        <div className="flex h-11 w-11 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground">
          {server.logoUrl ? (
            <img src={server.logoUrl} alt="" className="h-7 w-7 object-contain" loading="lazy" />
          ) : (
            <Server className="h-5 w-5" aria-hidden="true" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="break-words text-base font-semibold leading-6 text-foreground [overflow-wrap:anywhere]">{server.name}</h2>
          <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">
            {[server.category, server.provider, server.authType, server.transport].filter(Boolean).join(' / ')}
          </p>
        </div>
      </div>

      <p className="mt-4 line-clamp-4 min-h-20 break-words text-sm leading-6 text-muted-foreground [overflow-wrap:anywhere]">
        {server.description || t('mcpMarketplace.noDescription')}
      </p>

      <dl className="mt-4 grid gap-2 text-xs text-muted-foreground">
        <MetaRow label={t('mcpMarketplace.provider')} value={server.provider} />
        <MetaRow label={t('mcpMarketplace.authType')} value={server.authType} />
        <MetaRow label={t('mcpMarketplace.transport')} value={server.transport} />
        <MetaRow label={t('mcpMarketplace.sourceUrl')} value={server.url} breakAll />
      </dl>

      <div className="mt-4 flex flex-wrap gap-2">
        <StatusBadge active={server.available} label={server.available ? t('mcpMarketplace.available') : t('mcpMarketplace.unavailable')} />
        <StatusBadge active={server.registered} label={server.registered ? t('mcpMarketplace.registered') : t('mcpMarketplace.notRegistered')} />
        <StatusBadge active={server.secure} label={server.secure ? t('mcpMarketplace.secure') : t('mcpMarketplace.unverified')} icon="shield" />
        {server.requiresApiKey ? <StatusBadge active label={t('mcpMarketplace.requiresApiKey')} icon="key" /> : null}
        {server.requiresOauthConfig ? <StatusBadge active label={t('mcpMarketplace.requiresOauth')} icon="key" /> : null}
      </div>

      {server.tags.length > 0 ? <TagList tags={server.tags} /> : null}

      <div className="mt-auto flex flex-wrap gap-2 pt-5">
        {server.url ? <ExternalAnchor href={server.url} label={t('mcpMarketplace.openServer')} /> : null}
        {server.documentationUrl ? <ExternalAnchor href={server.documentationUrl} label={t('mcpMarketplace.openDocs')} /> : null}
      </div>
    </Card>
  )
}

function ConnectionUrl({ label, value }: { label: string; value?: string | null }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  if (!value) {
    return null
  }
  return (
    <div className="rounded-lg border bg-secondary/30 p-3">
      <div className="mb-2 flex items-center justify-between gap-3">
        <span className="text-xs font-medium text-muted-foreground">{label}</span>
        <button
          type="button"
          onClick={() => copy(value)}
          className="inline-flex h-7 items-center gap-1.5 rounded-md border bg-background px-2 text-xs font-medium text-foreground transition-colors hover:bg-secondary"
        >
          {copied ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <Copy className="h-3.5 w-3.5" aria-hidden="true" />}
          {copied ? t('copyButton.copied') : t('copyButton.copy')}
        </button>
      </div>
      <p className="break-all font-mono text-xs leading-5 text-foreground">{value}</p>
    </div>
  )
}

function MetaRow({ label, value, breakAll = false }: { label: string; value?: string | null; breakAll?: boolean }) {
  if (!value) {
    return null
  }
  return (
    <div className="grid grid-cols-[6.5rem_minmax(0,1fr)] gap-2">
      <dt className="font-medium text-foreground">{label}</dt>
      <dd className={cn('text-muted-foreground', breakAll ? 'break-all' : 'truncate')}>{value}</dd>
    </div>
  )
}

function TagList({ tags }: { tags: string[] }) {
  return (
    <div className="mt-4 flex flex-wrap gap-2">
      {tags.slice(0, 8).map((tag) => (
        <span key={tag} className="max-w-full truncate rounded-full bg-secondary px-2.5 py-1 text-xs text-muted-foreground">
          {tag}
        </span>
      ))}
    </div>
  )
}

function StatusBadge({ active, label, icon }: { active: boolean; label: string; icon?: 'shield' | 'key' }) {
  return (
    <span
      className={cn(
        'inline-flex max-w-full items-center gap-1.5 rounded-full px-2.5 py-1 text-xs',
        active ? 'bg-primary/10 text-primary' : 'bg-secondary text-muted-foreground',
      )}
    >
      {icon === 'shield' ? <ShieldCheck className="h-3.5 w-3.5 flex-none" aria-hidden="true" /> : null}
      {icon === 'key' ? <KeyRound className="h-3.5 w-3.5 flex-none" aria-hidden="true" /> : null}
      <span className="truncate">{label}</span>
    </span>
  )
}

function CountBadge({ label }: { label: string }) {
  return <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs text-muted-foreground">{label}</span>
}

function ExternalAnchor({ href, label }: { href: string; label: string }) {
  return (
    <a
      href={href}
      target="_blank"
      rel="noreferrer"
      className="inline-flex h-9 items-center justify-center gap-2 rounded-md border border-border bg-background px-3 text-sm font-medium text-foreground transition-colors hover:bg-secondary"
    >
      <ExternalLink className="h-4 w-4" aria-hidden="true" />
      {label}
    </a>
  )
}

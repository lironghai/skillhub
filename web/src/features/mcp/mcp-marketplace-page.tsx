import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { Check, Copy, ExternalLink, KeyRound, Loader2, RefreshCw, Search, Server, ShieldCheck, X } from 'lucide-react'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Input } from '@/shared/ui/input'
import { Table, TableBody, TableCell, TableRow } from '@/shared/ui/table'
import { cn } from '@/shared/lib/utils'
import { MAX_SEARCH_QUERY_LENGTH } from '@/shared/lib/search-query'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { MCP_CATALOG_PAGE_SIZE } from './mcp-catalog-query'
import type { McpAssociatedItem, McpCatalogItem, McpInternalServerItem } from './mcp-catalog-types'
import { useMcpCatalog, useMcpInternalServers } from './use-mcp-catalog'
import { SvgIcon } from '@/shared/components/svg-icon'

type McpMarketplaceTab = 'internal' | 'opensource'
type AssociatedDetailType = 'tools' | 'resources' | 'prompts'

export function McpMarketplacePage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<McpMarketplaceTab>('internal')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('list')

  const internalQuery = useMcpInternalServers({
    search,
    page,
    size: MCP_CATALOG_PAGE_SIZE,
  }, {
    enabled: activeTab === 'internal',
  })
  const openSourceQuery = useMcpCatalog({
    search,
    page,
    size: MCP_CATALOG_PAGE_SIZE,
  }, {
    enabled: activeTab === 'opensource',
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
        title={t("mcpMarketplace.title")}
        subtitle={t("mcpMarketplace.subtitle")}
        actions={
          <Button
            type="button"
            variant="outline"
            onClick={() => activeQuery.refetch()}
            disabled={activeQuery.isFetching}
            style={{ boxShadow: "0 2px 8px 0 rgba(159, 69, 66, 0.12)" }}
            className="bg-[#fff]"
          >
            {activeQuery.isFetching ? (
              <Loader2
                className="mr-2 h-4 w-4 animate-spin"
                aria-hidden="true"
              />
            ) : (
              // <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
              <SvgIcon name="svg-mcp_refresh" className="mr-2 h-4 w-4" />
            )}
            {t("mcpMarketplace.refresh")}
          </Button>
        }
      />
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          {/* <div>
            <TabButton
              active={activeTab === "internal"}
              onClick={() => handleTabChange("internal")}
            >
              {t("mcpMarketplace.internalTab")}
            </TabButton>
            <TabButton
              active={activeTab === "opensource"}
              onClick={() => handleTabChange("opensource")}
            >
              {t("mcpMarketplace.openSourceTab")}
            </TabButton>
          </div> */}
          <Button
            type="button"
            variant="outline"
            onClick={() => handleTabChange("internal")}
            style={{
              height: "32px",
              width: "auto",
              marginRight: "8px",
            }}
            className={cn(
              "rounded-[8px] px-2 text-sm font-medium transition-colors",
              activeTab === "internal"
                ? "bg-brand-gradient text-primary-foreground hover:text-primary-foreground "
                : "bg-[#fff] hover:text-foreground",
            )}
          >
            {t("mcpMarketplace.internalTab")}
          </Button>
          <Button
            type="button"
            variant="outline"
            onClick={() => handleTabChange("opensource")}
            style={{
              height: "32px",
              width: "auto",
            }}
            className={cn(
              "rounded-[8px] px-2 text-sm font-medium transition-colors",
              activeTab === "opensource"
                ? "bg-brand-gradient text-primary-foreground hover:text-primary-foreground "
                : "bg-[#fff] hover:text-foreground ",
            )}
          >
            {t("mcpMarketplace.openSourceTab")}
          </Button>
        </div>
        <div
          className="inline-flex border bg-card p-1 shadow-sm rounded-[8px] justify-center items-center  hover:border-primary/30"
          role="tablist"
          aria-label={t("mcpMarketplace.tabsLabel")}
        >
          <div>
            <TabButton
              active={viewMode === "list"}
              title={t("mcpMarketplace.listView")}
              style={{ marginRight: "4px" }}
              onClick={() => setViewMode("list")}
            >
              {/* <List className="h-4 w-4" aria-hidden="true" /> */}
              {/* {t("mcpMarketplace.listView")} */}
              <SvgIcon
                name="svg-mcp_list"
                className="h-4 w-4 color-[var(--brand-gradient)]"
                style={viewMode === "list" ? { color: "#fff" } : {}}
              />
            </TabButton>
            <TabButton
              active={viewMode === "grid"}
              title={t("mcpMarketplace.gridView")}
              onClick={() => setViewMode("grid")}
            >
              {/* <LayoutGrid className="h-4 w-4" aria-hidden="true" /> */}
              {/* {t("mcpMarketplace.gridView")} */}
              <SvgIcon
                name="svg-mcp_card"
                className="h-4 w-4 color-[var(--brand-gradient)]"
                style={viewMode === "grid" ? { color: "#fff" } : {}}
              />
            </TabButton>
          </div>
        </div>
      </div>

      <form
        onSubmit={handleSubmit}
        className="rounded-lg border bg-card p-3 shadow-sm"
      >
        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
              aria-hidden="true"
            />
            <Input
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              maxLength={MAX_SEARCH_QUERY_LENGTH}
              placeholder={
                activeTab === "internal"
                  ? t("mcpMarketplace.internalSearchPlaceholder")
                  : t("mcpMarketplace.searchPlaceholder")
              }
              className="h-11 pl-10 pr-10"
            />
            {searchInput ? (
              <button
                type="button"
                onClick={handleClear}
                className="absolute right-2 top-1/2 inline-flex h-8 w-8 -translate-y-1/2 items-center justify-center rounded-md text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground"
                aria-label={t("mcpMarketplace.clearSearch")}
                title={t("mcpMarketplace.clearSearch")}
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            ) : null}
          </div>
          <Button
            type="submit"
            className="sm:min-w-28"
            disabled={activeQuery.isFetching}
          >
            {activeQuery.isFetching ? (
              <Loader2
                className="mr-2 h-4 w-4 animate-spin"
                aria-hidden="true"
              />
            ) : (
              <Search className="mr-2 h-4 w-4" aria-hidden="true" />
            )}
            {t("mcpMarketplace.search")}
          </Button>
        </div>
      </form>

      {activeQuery.isError ? (
        <Card className="p-8">
          <EmptyState
            title={t("mcpMarketplace.errorTitle")}
            description={t("mcpMarketplace.errorDescription")}
            action={
              <Button
                type="button"
                variant="outline"
                onClick={() => activeQuery.refetch()}
              >
                <RefreshCw className="mr-2 h-4 w-4" aria-hidden="true" />
                {t("mcpMarketplace.retry")}
              </Button>
            }
          />
        </Card>
      ) : activeQuery.isLoading ? (
        <SkeletonList count={viewMode === "grid" ? 2 : 1} />
      ) : activeTab === "internal" ? (
        viewMode === "grid" ? (
          <InternalServerList
            servers={internalQuery.data?.items ?? []}
            search={search}
            onClear={handleClear}
          />
        ) : (
          <InternalServerTable
            servers={internalQuery.data?.items ?? []}
            search={search}
            onClear={handleClear}
          />
        )
      ) : viewMode === "grid" ? (
        <OpenSourceCatalogList
          servers={openSourceQuery.data?.items ?? []}
          search={search}
          onClear={handleClear}
        />
      ) : (
        <OpenSourceTable
          servers={openSourceQuery.data?.items ?? []}
          search={search}
          onClear={handleClear}
        />
      )}

      {!activeQuery.isError && !activeQuery.isLoading && totalPages > 1 ? (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={setPage}
        />
      ) : null}
    </div>
  );
}

function TabButton({ active, onClick, children, style, title }: { active: boolean; onClick: () => void; children: ReactNode; style?: React.CSSProperties; title?: string }) {
  return (
    <button
      type="button"
      role="tab"
      title={title}
      aria-selected={active}
      onClick={onClick}
      style={{ height: "28px", width: "28px", ...style }}
      className={cn(
        "rounded-[6px] px-1.5 text-sm font-medium transition-colors",
        active
          ? "bg-brand-gradient text-primary-foreground shadow-sm"
          : "text-muted-foreground hover:text-foreground",
      )}
    >
      {children}
    </button>
  );
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

function InternalServerTable({ servers, search, onClear }: { servers: McpInternalServerItem[]; search: string; onClear: () => void }) {
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
    <Card className="overflow-hidden">
      <Table>
        <TableBody>
          {servers.map((server) => (
            <InternalServerTableRow key={server.id} server={server} />
          ))}
        </TableBody>
      </Table>
    </Card>
  )
}

function InternalServerTableRow({ server }: { server: McpInternalServerItem }) {
  const { t } = useTranslation()
  const [detailType, setDetailType] = useState<AssociatedDetailType | null>(null)
  const detailItems = detailType ? server[detailType] : []
  const detailCount = detailType === 'tools'
    ? server.toolCount
    : detailType === 'resources'
      ? server.resourceCount
      : detailType === 'prompts'
        ? server.promptCount
        : 0
  const detailTitle = detailType ? t(associatedDetailTitleKey(detailType)) : ''
  //准备行点击参数
   const [rowDetail, setRowDetail] = useState<Boolean>(false)

  return (
    <>
      <TableRow className="align-top hover:bg-muted/50" onClick={() => setRowDetail(true)}>
        {/* Server Name */}
        <TableCell className='p-7'>
          <div className="flex gap-3">
            <div className="flex h-14 w-14 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground" style={{background: 'rgba(191,55,50,0.1)',boxShadow:"0 2px 8px rgba(159,69,66,0.12)" }}>
              {
                server.iconUrl ? (
                  <img src={server.iconUrl} alt="" className="h-8 w-8 object-contain" loading="lazy" />
                ) : (
                  // <Server className="h-8 w-8" aria-hidden="true" />
                  <SvgIcon name="svg-mcp_icon" className="h-8 w-8" />
                )
              }

            </div>
            <div className="min-w-0 flex-1">
              <div className='flex justify-between'>
                <p className="break-words text-sm font-semibold leading-5 text-foreground [overflow-wrap:anywhere]">{server.name}</p>
                <div className="flex flex-wrap gap-1.5">
                  <StatusBadge active={server.enabled} label={server.enabled ? t('mcpMarketplace.enabled') : t('mcpMarketplace.disabled')} />
                  <CountBadgeButton label={t('mcpMarketplace.toolsCount', { count: server.toolCount })} onClick={(event) => {event.stopPropagation(); setDetailType('tools')}} />
                  <CountBadgeButton label={t('mcpMarketplace.resourcesCount', { count: server.resourceCount })} onClick={(event) => {event.stopPropagation(); setDetailType('resources')}} />
                  <CountBadgeButton label={t('mcpMarketplace.promptsCount', { count: server.promptCount })} onClick={(event) => {event.stopPropagation(); setDetailType('prompts')}}   />
                </div>

              </div>
              
              <p className="mt-2 break-all text-xs leading-4 text-muted-foreground">{server.description}</p>
            </div>
          </div>
        </TableCell>
      </TableRow>
      {/* 点击行展示弹窗 */}
      <Dialog open={rowDetail === true} onOpenChange={(open) => {
        if (!open) {
          setRowDetail(false)
        }
      }}>
        <DialogContent className="w-[min(calc(100vw-2rem),40rem)] max-h-[calc(100vh-2rem)] overflow-hidden p-0">
          <DialogHeader className="border-b px-6 py-5 text-left">
            <DialogTitle className="text-left text-lg">{server.name}</DialogTitle>
            <DialogDescription className="text-left">
              {server.id}
            </DialogDescription>
          </DialogHeader>
          <div className='p-7 pt-0'>
            <div className="mt-0 space-y-3">
            <ConnectionUrl label={t('mcpMarketplace.streamableHttpUrl')} value={server.streamableHttpUrl} />
            <ConnectionUrl label={t('mcpMarketplace.sseUrl')} value={server.sseUrl} />
          </div>
          <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
            <MetaRow label={t('mcpMarketplace.owner')} value={server.ownerEmail} />
            <MetaRow label={t('mcpMarketplace.team')} value={server.team} />
            <MetaRow label={t('mcpMarketplace.visibility')} value={server.visibility} />
          </dl>
        </div>
        </DialogContent>
      </Dialog>
      {/* 点击按钮展示 */}
      <Dialog open={detailType !== null} onOpenChange={(open) => {
        if (!open) {
          setDetailType(null)
        }
      }}>
        <DialogContent className="w-[min(calc(100vw-2rem),40rem)] max-h-[calc(100vh-2rem)] overflow-hidden p-0">
          <DialogHeader className="border-b px-6 py-5 text-left">
            <DialogTitle className="text-left text-lg">{detailTitle}</DialogTitle>
            <DialogDescription className="text-left">
              {t('mcpMarketplace.associatedDialogDescription', {
                server: server.name,
                count: detailCount,
              })}
            </DialogDescription>
          </DialogHeader>
          <AssociatedDetailList
            items={detailItems}
            emptyLabel={t('mcpMarketplace.emptyAssociatedItems', { type: detailTitle })}
            unnamedLabel={t(detailType ? associatedDetailUnnamedKey(detailType) : 'mcpMarketplace.unnamedItem')}
          />
        </DialogContent>
      </Dialog>
    </>
  )
}

function InternalServerCard({ server }: { server: McpInternalServerItem }) {
  const { t } = useTranslation()
  const [detailType, setDetailType] = useState<AssociatedDetailType | null>(null)
  const detailItems = detailType ? server[detailType] : []
  const detailCount = detailType === 'tools'
    ? server.toolCount
    : detailType === 'resources'
      ? server.resourceCount
      : detailType === 'prompts'
        ? server.promptCount
        : 0
  const detailTitle = detailType ? t(associatedDetailTitleKey(detailType)) : ''

  return (
    <>
      <Card className="flex min-h-80 flex-col p-5">
        <div className="flex gap-3">
          <div
            className="flex h-11 w-11 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground"
            style={{
              background: "rgba(191,55,50,0.1)",
              boxShadow: "0 2px 8px rgba(159,69,66,0.12)",
            }}
          >
            {/* <Server className="h-5 w-5" aria-hidden="true" /> */}
            {server.iconUrl ? (
              <img
                src={server.iconUrl}
                alt=""
                className="h-8 w-8 object-contain"
                loading="lazy"
              />
            ) : (
              // <Server className="h-5 w-5 color-[var(--brand-gradient)]" aria-hidden="true" />
              <SvgIcon name="svg-mcp_icon" className="h-5 w-5" />
            )}
          </div>
          <div className="min-w-0 flex-1">
            <h2 className="break-words text-base font-semibold leading-6 text-foreground [overflow-wrap:anywhere]">
              {server.name}
            </h2>
            <p className="mt-1 break-all text-xs leading-5 text-muted-foreground">
              {server.id}
            </p>
          </div>
        </div>

        <p className="mt-4 min-h-12 break-words text-sm leading-6 text-muted-foreground [overflow-wrap:anywhere]">
          {server.description || t("mcpMarketplace.noDescription")}
        </p>

        <div className="mt-4 flex flex-wrap gap-2">
          <StatusBadge
            active={server.enabled}
            label={
              server.enabled
                ? t("mcpMarketplace.enabled")
                : t("mcpMarketplace.disabled")
            }
          />
          <CountBadgeButton
            label={t("mcpMarketplace.toolsCount", { count: server.toolCount })}
            onClick={() => setDetailType("tools")}
          />
          <CountBadgeButton
            label={t("mcpMarketplace.resourcesCount", {
              count: server.resourceCount,
            })}
            onClick={() => setDetailType("resources")}
          />
          <CountBadgeButton
            label={t("mcpMarketplace.promptsCount", {
              count: server.promptCount,
            })}
            onClick={() => setDetailType("prompts")}
          />
        </div>

        {server.tags.length > 0 ? <TagList tags={server.tags} /> : null}

        <div className="mt-5 space-y-3">
          <ConnectionUrl
            label={t("mcpMarketplace.streamableHttpUrl")}
            value={server.streamableHttpUrl}
          />
          <ConnectionUrl
            label={t("mcpMarketplace.sseUrl")}
            value={server.sseUrl}
          />
        </div>

        <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
          <MetaRow
            label={t("mcpMarketplace.owner")}
            value={server.ownerEmail}
          />
          <MetaRow label={t("mcpMarketplace.team")} value={server.team} />
          <MetaRow
            label={t("mcpMarketplace.visibility")}
            value={server.visibility}
          />
        </dl>
      </Card>
      <Dialog
        open={detailType !== null}
        onOpenChange={(open) => {
          if (!open) {
            setDetailType(null);
          }
        }}
      >
        <DialogContent className="w-[min(calc(100vw-2rem),40rem)] max-h-[calc(100vh-2rem)] overflow-hidden p-0">
          <DialogHeader className="border-b px-6 py-5 text-left">
            <DialogTitle className="text-left text-lg">
              {detailTitle}
            </DialogTitle>
            <DialogDescription className="text-left">
              {t("mcpMarketplace.associatedDialogDescription", {
                server: server.name,
                count: detailCount,
              })}
            </DialogDescription>
          </DialogHeader>
          <AssociatedDetailList
            items={detailItems}
            emptyLabel={t("mcpMarketplace.emptyAssociatedItems", {
              type: detailTitle,
            })}
            unnamedLabel={t(
              detailType
                ? associatedDetailUnnamedKey(detailType)
                : "mcpMarketplace.unnamedItem",
            )}
          />
        </DialogContent>
      </Dialog>
    </>
  );
}

export function AssociatedDetailList({
  items,
  emptyLabel,
  unnamedLabel,
}: {
  items?: McpAssociatedItem[]
  emptyLabel: string
  unnamedLabel: string
}) {
  const visibleItems = (items ?? []).filter((item) => item.name || item.id || item.description)
  if (visibleItems.length === 0) {
    return (
      <div className="px-6 py-8 text-center text-sm text-muted-foreground">
        {emptyLabel}
      </div>
    )
  }

  return (
    <ul className="max-h-[60vh] space-y-3 overflow-y-auto px-6 py-5">
      {visibleItems.map((item, index) => {
        const name = item.name || item.id || unnamedLabel
        return (
          <li key={`${item.id ?? index}-${name}`} className="min-w-0 rounded-lg border bg-secondary/20 p-3">
            <p className="break-words text-sm font-medium leading-5 text-foreground [overflow-wrap:anywhere]">{name}</p>
            {item.description ? (
              <p className="mt-1 break-words text-xs leading-5 text-muted-foreground [overflow-wrap:anywhere]">
                {item.description}
              </p>
            ) : null}
          </li>
        )
      })}
    </ul>
  )
}

function OpenSourceServerCard({ server }: { server: McpCatalogItem }) {
  const { t } = useTranslation()
  return (
    <Card className="flex min-h-96 flex-col p-5">
      <div className="flex gap-3">
        <div
          className="flex h-11 w-11 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground"
          style={{
            background: "rgba(191,55,50,0.1)",
            boxShadow: "0 2px 8px rgba(159,69,66,0.12)",
          }}
        >
          {server.iconUrl ? (
            <img
              src={server.iconUrl}
              alt=""
              className="h-7 w-7 object-contain"
              loading="lazy"
            />
          ) : (
            // <Server className="h-5 w-5" aria-hidden="true" />
            <SvgIcon name="svg-mcp_icon" className="h-5 w-5" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="break-words text-base font-semibold leading-6 text-foreground [overflow-wrap:anywhere]">
            {server.name}
          </h2>
          <p className="mt-1 line-clamp-2 text-xs leading-5 text-muted-foreground">
            {[
              server.category,
              server.provider,
              server.authType,
              server.transport,
            ]
              .filter(Boolean)
              .join(" / ")}
          </p>
        </div>
      </div>

      <p className="mt-4 line-clamp-4 min-h-20 break-words text-sm leading-6 text-muted-foreground [overflow-wrap:anywhere]">
        {server.description || t("mcpMarketplace.noDescription")}
      </p>

      <dl className="mt-4 grid gap-2 text-xs text-muted-foreground">
        <MetaRow label={t("mcpMarketplace.provider")} value={server.provider} />
        <MetaRow label={t("mcpMarketplace.authType")} value={server.authType} />
        <MetaRow
          label={t("mcpMarketplace.transport")}
          value={server.transport}
        />
        <MetaRow
          label={t("mcpMarketplace.sourceUrl")}
          value={server.url}
          breakAll
        />
      </dl>

      <div className="mt-4 flex flex-wrap gap-2">
        <StatusBadge
          active={server.available}
          label={
            server.available
              ? t("mcpMarketplace.available")
              : t("mcpMarketplace.unavailable")
          }
        />
        <StatusBadge
          active={server.registered}
          label={
            server.registered
              ? t("mcpMarketplace.registered")
              : t("mcpMarketplace.notRegistered")
          }
        />
        <StatusBadge
          active={server.secure}
          label={
            server.secure
              ? t("mcpMarketplace.secure")
              : t("mcpMarketplace.unverified")
          }
          icon="shield"
        />
        {server.requiresApiKey ? (
          <StatusBadge
            active
            label={t("mcpMarketplace.requiresApiKey")}
            icon="key"
          />
        ) : null}
        {server.requiresOauthConfig ? (
          <StatusBadge
            active
            label={t("mcpMarketplace.requiresOauth")}
            icon="key"
          />
        ) : null}
      </div>

      {server.tags.length > 0 ? <TagList tags={server.tags} /> : null}

      <div className="mt-auto flex flex-wrap gap-2 pt-5">
        {server.url ? (
          <ExternalAnchor
            href={server.url}
            label={t("mcpMarketplace.openServer")}
          />
        ) : null}
        {server.documentationUrl ? (
          <ExternalAnchor
            href={server.documentationUrl}
            label={t("mcpMarketplace.openDocs")}
          />
        ) : null}
      </div>
    </Card>
  );
}

function OpenSourceTable({
  servers,
  search,
  onClear,
}: {
  servers: McpCatalogItem[];
  search: string;
  onClear: () => void;
}) {
  const { t } = useTranslation();
  if (servers.length === 0) {
    return (
      <Card className="p-8">
        <EmptyState
          title={t("mcpMarketplace.emptyInternalTitle")}
          description={
            search
              ? t("mcpMarketplace.emptySearchDescription")
              : t("mcpMarketplace.emptyInternalDescription")
          }
          action={
            search ? (
              <Button type="button" variant="outline" onClick={onClear}>
                {t("mcpMarketplace.clearSearch")}
              </Button>
            ) : null
          }
        />
      </Card>
    );
  }
  return (
    <Card className="overflow-hidden">
      <Table>
        <TableBody>
          {servers.map((server) => (
            <OpenSourceTableRow key={server.id} server={server} />
          ))}
        </TableBody>
      </Table>
    </Card>
  );
}

function OpenSourceTableRow({ server }: { server: McpCatalogItem }) {
  const { t } = useTranslation();
  // const [detailType, setDetailType] = useState<AssociatedDetailType | null>(null)
  // const detailItems = detailType ? server[detailType] : []
  // const detailCount = detailType === 'tools'
  //   ? server.toolCount
  //   : detailType === 'resources'
  //     ? server.resourceCount
  //     : detailType === 'prompts'
  //       ? server.promptCount
  //       : 0
  // const detailTitle = detailType ? t(associatedDetailTitleKey(detailType)) : ''
  // //准备行点击参数
  const [rowDetail, setRowDetail] = useState<Boolean>(false);

  return (
    <>
      <TableRow
        className="align-top hover:bg-muted/50"
        onClick={() => setRowDetail(true)}
      >
        {/* Server Name */}
        <TableCell className="p-7">
          <div className="flex gap-3">
            <div
              className="flex h-14 w-14 flex-none items-center justify-center rounded-lg bg-secondary text-muted-foreground"
              style={{
                background: "rgba(191,55,50,0.1)",
                boxShadow: "0 2px 8px rgba(159,69,66,0.12)",
              }}
            >
              {server.iconUrl ? (
                <img
                  src={server.iconUrl}
                  alt=""
                  className="h-8 w-8 object-contain"
                  loading="lazy"
                />
              ) : (
                // <Server className="h-8 w-8" aria-hidden="true" />
                <SvgIcon name="svg-mcp_icon" className="h-8 w-8" />
              )}
            </div>
            <div className="min-w-0 flex-1">
              <div className="flex justify-between">
                <p className="break-words text-sm font-semibold leading-5 text-foreground [overflow-wrap:anywhere]">
                  {server.name}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  <StatusBadge
                    active={server.available}
                    label={
                      server.available
                        ? t("mcpMarketplace.available")
                        : t("mcpMarketplace.unavailable")
                    }
                  />
                  <StatusBadge
                    active={server.registered}
                    label={
                      server.registered
                        ? t("mcpMarketplace.registered")
                        : t("mcpMarketplace.notRegistered")
                    }
                  />
                  <StatusBadge
                    active={server.secure}
                    label={
                      server.secure
                        ? t("mcpMarketplace.secure")
                        : t("mcpMarketplace.unverified")
                    }
                    icon="shield"
                  />
                  {server.requiresApiKey ? (
                    <StatusBadge
                      active
                      label={t("mcpMarketplace.requiresApiKey")}
                      icon="key"
                    />
                  ) : null}
                  {server.requiresOauthConfig ? (
                    <StatusBadge
                      active
                      label={t("mcpMarketplace.requiresOauth")}
                      icon="key"
                    />
                  ) : null}
                </div>
              </div>

              <p className="mt-2 break-all text-xs leading-4 text-muted-foreground">
                {server.description}
              </p>
            </div>
          </div>
        </TableCell>
      </TableRow>
      {/* 点击行展示弹窗 */}
      <Dialog
        open={rowDetail === true}
        onOpenChange={(open) => {
          if (!open) {
            setRowDetail(false);
          }
        }}
      >
        <DialogContent className="w-[min(calc(100vw-2rem),40rem)] max-h-[calc(100vh-2rem)] overflow-hidden p-0">
          <DialogHeader className="border-b px-6 py-5 text-left">
            <DialogTitle className="text-left text-lg">
              {server.name}
            </DialogTitle>
            <DialogDescription className="text-left">
              {[
                server.category,
                server.provider,
                server.authType,
                server.transport,
              ]
                .filter(Boolean)
                .join(" / ")}
            </DialogDescription>
          </DialogHeader>
          <div className="p-7 pt-0">
            <div>
              {server.tags.length > 0 ? (
                <TagList className="mt-1" tags={server.tags} />
              ) : null}
            </div>
            <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
              <MetaRow
                label={t("mcpMarketplace.provider")}
                value={server.provider}
              />
              <MetaRow
                label={t("mcpMarketplace.authType")}
                value={server.authType}
              />
              <MetaRow
                label={t("mcpMarketplace.transport")}
                value={server.transport}
              />
              <MetaRow
                label={t("mcpMarketplace.sourceUrl")}
                value={server.url}
                breakAll
              />
            </dl>
            <div className="mt-auto flex flex-wrap gap-2 pt-5">
              {server.url ? (
                <ExternalAnchor
                  href={server.url}
                  label={t("mcpMarketplace.openServer")}
                />
              ) : null}
              {server.documentationUrl ? (
                <ExternalAnchor
                  href={server.documentationUrl}
                  label={t("mcpMarketplace.openDocs")}
                />
              ) : null}
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
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

function TagList({ tags, className }: { tags: string[]; className?: string }) {
  return (
    <div className={cn("mt-4 flex flex-wrap gap-2", className)}>
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
      style={{ background: active ? 'rgba(24, 168, 120, 0.10)' : '', color: active ? '#18A878' : '' }}
    >
      {icon === 'shield' ? <ShieldCheck className="h-3.5 w-3.5 flex-none" aria-hidden="true" /> : null}
      {icon === 'key' ? <KeyRound className="h-3.5 w-3.5 flex-none" aria-hidden="true" /> : null}
      <span className="truncate">{label}</span>
    </span>
  )
}

function CountBadgeButton({ label, onClick }: { label: string; onClick: (event: { stopPropagation: () => void }) => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs text-muted-foreground transition-colors hover:bg-primary/10 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
    >
      {label}
    </button>
  )
}

function associatedDetailTitleKey(type: AssociatedDetailType) {
  switch (type) {
    case 'tools':
      return 'mcpMarketplace.toolsTitle'
    case 'resources':
      return 'mcpMarketplace.resourcesTitle'
    case 'prompts':
      return 'mcpMarketplace.promptsTitle'
  }
}

function associatedDetailUnnamedKey(type: AssociatedDetailType) {
  switch (type) {
    case 'tools':
      return 'mcpMarketplace.unnamedTool'
    case 'resources':
      return 'mcpMarketplace.unnamedResource'
    case 'prompts':
      return 'mcpMarketplace.unnamedPrompt'
  }
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

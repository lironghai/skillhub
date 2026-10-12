import { useMemo, useState, type FormEvent } from 'react'
import { Link } from "@tanstack/react-router";
import { useTranslation } from 'react-i18next'
import { Info, Check, Copy, ExternalLink, KeyRound, Loader2, RefreshCw, ShieldCheck, X } from 'lucide-react'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/shared/ui/dialog'
import { Table, TableBody, TableCell, TableRow } from '@/shared/ui/table'
import { cn } from '@/shared/lib/utils'
import { MAX_SEARCH_QUERY_LENGTH } from '@/shared/lib/search-query'
import { useCopyToClipboard } from '@/shared/lib/clipboard'
import { MCP_CATALOG_PAGE_SIZE } from './mcp-catalog-query'
import type { McpAssociatedItem, McpCatalogItem, McpInternalServerItem } from './mcp-catalog-types'
import { useMcpCatalog, useMcpInternalServers } from './use-mcp-catalog'
import { SvgIcon } from '@/shared/components/svg-icon'
import { SearchBar } from "@/features/search/search-bar";

type McpMarketplaceTab = 'internal' | 'opensource'
type AssociatedDetailType = 'tools' | 'resources' | 'prompts'

const mcpFeatureDateTimePattern = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/

export function formatMcpFeatureDateTime(value?: string | null) {
  const trimmed = value?.trim()
  if (!trimmed) {
    return '-'
  }

  const match = trimmed.match(mcpFeatureDateTimePattern)
  if (!match) {
    return trimmed
  }

  const [, year, month, day, hour, minute] = match
  return `${Number(year)}/${Number(month)}/${Number(day)} ${hour}:${minute}`
}

export function McpMarketplacePage() {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<McpMarketplaceTab>('internal')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)
  const [viewMode] = useState<'grid' | 'list'>('list')

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
      />

      <Button
        style={{
          borderRadius: "12px",
          position: "absolute",
          top: "18px",
          right: "0",
          padding: "26px 32px",
          marginTop: "0",
          color: "#000",
        }}
        className="bg-background text-[16px] hover:bg-accent hover:text-accent-foreground "
        onClick={() => activeQuery.refetch()}
        disabled={activeQuery.isFetching}
      >
        {activeQuery.isFetching ? (
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
        ) : (
          // <RefreshCw className="h-4 w-4" aria-hidden="true" />
          <SvgIcon name="svg-mcp_refresh" className="h-4 w-4" />
        )}
        {t("mcpMarketplace.refresh")}
      </Button>

      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
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
                ? "bg-brand-gradient text-white hover:opacity-90 hover:text-white"
                : "bg-background text-foreground hover:bg-accent hover:text-accent-foreground",
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
                ? "bg-brand-gradient text-white hover:opacity-90 hover:text-white"
                : "bg-background text-foreground hover:bg-accent hover:text-accent-foreground",
            )}
          >
            {t("mcpMarketplace.openSourceTab")}
          </Button>
        </div>
      </div>

      {/* Search Bar */}
      <div className="mt-6 flex items-center justify-between">
        <div className="flex-1">
          <SearchBar
            value={searchInput}
            onChange={setSearchInput}
            onSearch={setSearchInput}
            maxLength={MAX_SEARCH_QUERY_LENGTH}
            placeholder={
              activeTab === "internal"
                ? t("mcpMarketplace.internalSearchPlaceholder")
                : t("mcpMarketplace.searchPlaceholder")
            }
          />
        </div>
      </div>

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
        (
          <InternalServerTable
            servers={internalQuery.data?.items ?? []}
            search={search}
            onClear={handleClear}
          />
        )
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
   const [rowDetail, setRowDetail] = useState<boolean>(false)

  const features = [
    {
      title: "工具数量",
      value: server.toolCount + "个工具",
    },
    {
      title: "可见性",
      value: server.visibility == 'team'?"团队内":server.visibility == 'public'?"公共":"私有",
    },
    {
      title: "服务状态",
      value: server.enabled ? t('mcpMarketplace.enabled') : t('mcpMarketplace.disabled'),
    },
    {
      title: "版本",
      value: "v" + server.version,
    },
    {
      title: "创建时间",
      value: formatMcpFeatureDateTime(server.createdAt),
    },
    {
      title: "更新时间",
      value: formatMcpFeatureDateTime(server.updatedAt),
    },
  ] satisfies Array<{ title: string; value?: string | number | null }>;

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
        <DialogContent className="w-[min(calc(100vw-2rem),42rem)] max-h-[calc(100vh-2rem)] overflow-hidden p-0 [&>button>svg]:h-5 [&>button>svg]:w-5">
          
          <DialogHeader className="border-b px-6 py-5 text-left">
            <DialogTitle className="text-left text-[20px]" style={{color: '#333'}}> {server.name} </DialogTitle>
            <DialogDescription className="text-left" style={{color: '#666'}}>
              {server.description}
            </DialogDescription>
          </DialogHeader>
          <div className="grid grid-cols-1 overflow-hidden md:grid-cols-2 lg:grid-cols-3 mx-7 border-b">
            {features.map((feature) => (
              <div key={feature.title} className="bg-card py-3 transition-colors">
                <p className="mb-1 text-sm text-foreground text-muted-foreground ">{feature.title}</p>
                <p className="text-sm leading-relaxed  font-semibold">{feature.title == '可见性'? (<VisibilityStatus visibility={server.visibility} label={feature.value ?? '-'} />) : feature.title == '服务状态'? (<StatusBadge active={server.enabled} label={feature.value ?? '-'} />) : (feature.value ?? '-')}</p>
              </div>
            ))}
          </div>
          <div className="min-w-0 p-4 pt-0 sm:p-7 sm:pt-0">
            <div className="mt-0 space-y-2">
            <div className="text-sm font-bold" style={{color: '#333'}}>接入地址</div>
            <ConnectionUrl value={server.streamableHttpUrl} />
            {/* <ConnectionUrl label={t('mcpMarketplace.sseUrl')} value={server.sseUrl} /> */}
          </div>
          {/* <div className="text-sm font-bold">连接认证</div> */}
          <div className='pt-5 pb-3'>
            <h3 className="text-sm font-semibold text-foreground pb-2" style={{color: '#333'}}>{t('mcpMarketplace.tokenTitle')}</h3>
            <p className="text-[13px] leading-5 text-muted-foreground" style={{color: '#666'}}>在 MCP 客户端的 headers 配置中填写 Authorization 请求头，值为<span className='bg-[#f9f9f9] rounded-md p-1' style={{color: '#ED6C30'}}> {`Bearer <YOUR_ACCESS_TOKEN>`} </span>,不要把令牌放进 URL。</p>
          </div>
          <McpTokenUsageHint server={server} />
          <div className='pt-5 pb-3'>
            <h3 className="text-sm font-semibold text-foreground pb-2" style={{color: '#333'}}>Token获取</h3>
            <div className="text-[13px] leading-5 inline-flex items-center"  style={{color: '#666'}}>
              在
              <Link
                to="/dashboard/tokens"
                className="px-1"
                style={{color:'#BF3732'}}
              >
                API Tokens页面
              </Link>
              创建sk_开头的个人令牌.
              <div className='ml-3 px-2 inline-flex items-center rounded-[12px]' style={{height:"24px",color:"#333",fontSize:"12px",border:'1px solid rgba(191, 55, 50, 0.20)',background:"linear-gradient(0deg, rgba(191, 55, 50, 0.05) 0%, rgba(191, 55, 50, 0.05) 100%), #FFF"}}>
                <Info className="mr-1 inline-block h-4 w-4 align-text-bottom" style={{color:"#ED6C30"}}/>
                不要使用或分享账号密码</div>
            </div>
          </div>
            
          {/* <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
            <MetaRow label={t('mcpMarketplace.team')} value={server.team} />
            <MetaRow label={t('mcpMarketplace.visibility')} value={server.visibility} />
          </dl> */}
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
            value={server.streamableHttpUrl}
          />
          <ConnectionUrl
            value={server.sseUrl}
          />
        </div>
        <McpTokenUsageHint server={server} />

        <dl className="mt-auto grid gap-2 pt-5 text-xs text-muted-foreground">
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

export function McpTokenUsageHint({ server }: { server: Pick<McpInternalServerItem, 'name' | 'streamableHttpUrl'> }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  const authorization = 'Bearer <YOUR_ACCESS_TOKEN_HERE>'
  const config = JSON.stringify({
    [server.name]: {
      type: 'http',
      url: server.streamableHttpUrl,
      headers: { Authorization: authorization },
    },
  }, null, 2)
  return (
    <section className="space-y-2 rounded-lg border bg-[#0f0f0f] px-4 py-3">
      
      {/* <pre className="overflow-x-auto rounded-md bg-background p-3 text-xs leading-5 text-foreground"><code>{`Authorization: ${authorization}`}</code></pre> */}
      {server.streamableHttpUrl && (
        <div className="min-w-0 rounded-md">
          <div className="absolute right-10">
            <button
              type="button"
              onClick={() => copy(config)}
              className="inline-flex h-7 items-center gap-1.5 rounded-md border px-2 text-xs font-medium text-foreground transition-colors hover:bg-secondary"
              style={{ background: "#232323",color: "#ccc",borderColor: "#666" }}
            >
              {copied ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <Copy className="h-3.5 w-3.5" aria-hidden="true" />}
              {copied ? t('copyButton.copied') : t('复制配置')}
            </button>
          </div>
          <pre className="overflow-x-auto text-xs leading-5 " style={{ color: "#ccc"}}><code>{config}</code></pre>
        </div>
      )}
      {/* <p className="text-xs leading-5 text-muted-foreground">{t('mcpMarketplace.tokenScopeHint')}</p> */}
    </section>
  )
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

export function OpenSourceServerCard({ server }: { server: McpCatalogItem }) {
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
          {server.logoUrl ? (
            <img
              src={server.logoUrl}
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
          title={t("mcpMarketplace.emptyTitle")}
          description={
            search
              ? t("mcpMarketplace.emptySearchDescription")
              : t("mcpMarketplace.emptyDescription")
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
  const [rowDetail, setRowDetail] = useState<boolean>(false);

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
              {server.logoUrl ? (
                <img
                  src={server.logoUrl}
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

function ConnectionUrl({ value }: { value?: string | null }) {
  const { t } = useTranslation()
  const [copied, copy] = useCopyToClipboard()
  if (!value) {
    return null
  }
  return (
    <div className="rounded-lg border bg-secondary/30 p-3 flex items-center justify-between">
      <p className="break-all font-mono text-xs leading-5 text-foreground">{value}</p>
      <button
          type="button"
          onClick={() => copy(value)}
          className="ml-20 flex h-7 justify-center items-center gap-1.5 rounded-md border bg-background px-1 text-xs font-medium text-foreground transition-colors hover:bg-secondary"
          style={{ minWidth: "4.2rem" }}
        >
          {copied ? <Check className="h-3.5 w-3.5" aria-hidden="true" /> : <Copy className="h-3.5 w-3.5" aria-hidden="true" />}
          {copied ? t('copyButton.copied') : t('copyButton.copy')}
      </button>
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
function VisibilityStatus({ visibility, label }: { visibility: string, label: string }) {
  return (
    <span
      className={cn(
        "inline-flex max-w-full items-center gap-1.5 rounded-full px-2.5 py-1 text-xs bg-primary/10 text-primary"
      )}
      style={{
        background:
          visibility == "public"
            ? "rgba(24, 168, 120, 0.10)"
            : visibility == "team"
              ? "#ed6c301a"
              : "#f7f7f8",
        color:
          visibility == "public"
            ? "#18A878"
            : visibility == "team"
              ? "#ED6C30"
              : "#737373",
      }}
    >
      <span className="truncate">{label}</span>
    </span>
  );
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

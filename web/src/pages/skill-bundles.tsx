import { useNavigate, useSearch } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Boxes, Plus, Search } from 'lucide-react'
import { Input } from '@/shared/ui/input'
import { Button } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useVisibleLabels } from '@/shared/hooks/use-label-queries'
import { useSearchSkillBundles } from '@/shared/hooks/use-skill-bundle-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'

const PAGE_SIZE = 12

function getExpertInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase() || 'EP'
}

export function SkillBundlesPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const search = useSearch({ from: '/skill-bundles' })

  const q = normalizeSearchQuery(search.q || '')
  const namespace = (search.namespace || '').replace(/^@/, '')
  const selectedLabel = search.label || ''
  const sort = search.sort || 'newest'
  const page = search.page ?? 0

  const { data, isLoading, isFetching } = useSearchSkillBundles({
    q,
    namespace: namespace || undefined,
    label: selectedLabel || undefined,
    sort,
    page,
    size: PAGE_SIZE,
  })
  const { data: labels } = useVisibleLabels()
  const skillTypeLabels = (labels ?? []).filter((label) => label.type === 'RECOMMENDED')
  const items = data?.items ?? []
  const totalPages = data ? Math.ceil(data.total / data.size) : 0

  const updateSearch = (next: Partial<typeof search>) => {
    navigate({
      to: '/skill-bundles',
      search: {
        q,
        namespace,
        label: selectedLabel,
        sort,
        page,
        ...next,
      },
    })
  }

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <div className="space-y-3">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-secondary/70 text-primary">
              <Boxes className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-2xl font-semibold font-heading text-foreground">{t('skillBundles.title')}</h1>
              <p className="text-sm text-muted-foreground">{t('skillBundles.subtitle')}</p>
            </div>
          </div>
          <Button
            type="button"
            className="self-start"
            onClick={() => navigate({ to: '/dashboard/skill-bundles' })}
          >
            <Plus className="mr-2 h-4 w-4" />
            {t('skillBundles.createAction')}
          </Button>
        </div>
        <div className="flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              type="search"
              value={q}
              onChange={(event) => updateSearch({ q: event.target.value, page: 0 })}
              placeholder={t('skillBundles.searchPlaceholder')}
              className="pl-9"
            />
          </div>
          <Input
            type="search"
            value={namespace}
            onChange={(event) => updateSearch({ namespace: event.target.value.replace(/^@/, ''), page: 0 })}
            placeholder={t('skillBundles.namespacePlaceholder')}
            className="sm:max-w-[14rem]"
          />
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-muted-foreground">{t('skillBundles.sort.label')}</span>
          {(['newest', 'name'] as const).map((option) => (
            <Button
              key={option}
              type="button"
              size="sm"
              variant={sort === option ? 'default' : 'outline'}
              onClick={() => updateSearch({ sort: option, page: 0 })}
            >
              {t(`skillBundles.sort.${option}`)}
            </Button>
          ))}
          {data && data.total > 0 ? (
            <span className="text-sm text-muted-foreground">{t('skillBundles.results', { count: data.total })}</span>
          ) : null}
          {isFetching && !isLoading ? (
            <span className="text-sm text-muted-foreground">{t('skillBundles.updating')}</span>
          ) : null}
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <span className="text-sm font-medium text-muted-foreground">{t('skillBundles.filters.label')}</span>
          {skillTypeLabels.map((label) => (
            <Button
              key={label.slug}
              type="button"
              size="sm"
              variant={selectedLabel === label.slug ? 'default' : 'outline'}
              onClick={() => updateSearch({ label: selectedLabel === label.slug ? '' : label.slug, page: 0 })}
            >
              {label.displayName}
            </Button>
          ))}
        </div>
      </div>

      {isLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : items.length > 0 ? (
        <>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
            {items.map((bundle, index) => (
              <Card
                key={bundle.id}
                className={`h-full cursor-pointer p-5 transition-colors hover:border-primary/40 animate-fade-up delay-${Math.min(index % 6 + 1, 6)}`}
                onClick={() => navigate({ to: `/skill-bundles/${bundle.namespace}/${encodeURIComponent(bundle.slug)}` })}
              >
                <div className="space-y-4">
                  <div>
                    <div className="mb-2 flex items-start justify-between gap-3">
                      <div className="flex min-w-0 items-start gap-3">
                        <div className="flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded-xl bg-brand-gradient text-sm font-semibold text-white shadow-sm">
                          {bundle.avatarUrl ? (
                            <img src={bundle.avatarUrl} alt={bundle.name} className="h-full w-full object-cover" />
                          ) : (
                            <span aria-hidden="true">{getExpertInitials(bundle.name)}</span>
                          )}
                        </div>
                        <h2 className="line-clamp-2 text-lg font-semibold font-heading text-foreground">{bundle.name}</h2>
                      </div>
                      <NamespaceBadge type="TEAM" name={`@${bundle.namespace}`} />
                    </div>
                    {bundle.status !== 'PUBLISHED' ? (
                      <span className="status-pill mb-2">{t(`skillBundles.status.${bundle.status}`, { defaultValue: bundle.status })}</span>
                    ) : null}
                    {bundle.summary ? (
                      <p className="mt-2 line-clamp-3 text-sm leading-relaxed text-muted-foreground">{bundle.summary}</p>
                    ) : null}
                  </div>
                  {bundle.roleDescription ? (
                    <p className="line-clamp-2 text-sm text-muted-foreground">{bundle.roleDescription}</p>
                  ) : null}
                  <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
                    <span>{t('skillBundles.skillCount', { count: bundle.skillCount })}</span>
                    {(bundle.labels ?? []).slice(0, 3).map((label) => (
                      <span key={label.slug} className="rounded-md bg-secondary px-2 py-1 text-foreground">
                        {label.displayName}
                      </span>
                    ))}
                  </div>
                </div>
              </Card>
            ))}
          </div>
          {totalPages > 1 ? (
            <Pagination page={page} totalPages={totalPages} onPageChange={(nextPage) => updateSearch({ page: nextPage })} />
          ) : null}
        </>
      ) : (
        <EmptyState
          title={t('skillBundles.emptyTitle')}
          description={q ? t('skillBundles.emptyForQuery', { q }) : t('skillBundles.emptyDescription')}
          action={(
            <Button type="button" onClick={() => navigate({ to: '/dashboard/skill-bundles' })}>
              {t('skillBundles.createAction')}
            </Button>
          )}
        />
      )}
    </div>
  )
}

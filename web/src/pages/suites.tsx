import { useState } from 'react'
import { useNavigate } from "@tanstack/react-router";
import { useTranslation } from 'react-i18next'
import { ResourceCard } from '@/features/suite/resource-card'
import { useResourceSearch } from '@/shared/hooks/use-suite-queries'
import { EmptyState } from '@/shared/components/empty-state'
import { Pagination } from '@/shared/components/pagination'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { Button } from '@/shared/ui/button'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { DashboardPageHeader } from "@/shared/components/dashboard-page-header";
import { SearchBar } from "@/features/search/search-bar";


const PAGE_SIZE = 12

export function SuitesPage() {
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [page, setPage] = useState(0)
  const { data, isLoading } = useResourceSearch({
    q: query.trim() || undefined,
    resourceType: 'SUITE',
    sort: 'newest',
    page,
    size: PAGE_SIZE,
  })

  return (
    <div className={APP_SHELL_PAGE_CLASS_NAME}>
      <DashboardPageHeader
        title={t("suite.listTitle")}
        subtitle={t("suite.listDescription")}
      />
      <Button style={{borderRadius:'12px',position:'absolute',top:'18px',right:'0',padding:'26px 32px',fontSize:'16px',marginTop:'0'}} onClick={() => navigate({ to: "/dashboard/suites/new" })}>
        {t("suite.create")}
      </Button>

      {/* Search Bar */}
      <div className="mt-6 flex items-center justify-between">
        <div className="flex-1">
          <SearchBar
            value={query}
            onChange={(v) => {
              setQuery(v);
              setPage(0);
            }}
            onSearch={(v) => {
              setQuery(v);
              setPage(0);
            }}
          />
        </div>
      </div>
      {/* <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-end">
        <div>
          <h1 className="text-4xl font-semibold" style={{ color: '#333',marginBottom:'0.5rem' }}>{t("suite.listTitle")}</h1>
          <p className="mt-2 text-lg" style={{ color: '#666' }}>
            {t("suite.listDescription")}
          </p>
        </div>
        <Button onClick={() => navigate({ to: "/dashboard/suites/new" })}>
          {t("suite.create")}
        </Button>
      </div>

      <Input
        className="max-w-xl"
        value={query}
        placeholder={t("suite.searchPlaceholder")}
        onChange={(event) => {
          setQuery(event.target.value);
          setPage(0);
        }}
      /> */}

      {isLoading ? (
        <SkeletonList count={PAGE_SIZE} />
      ) : data?.items.length ? (
        <>
          <div className="grid grid-cols-1 gap-5 md:grid-cols-2 lg:grid-cols-3">
            {data.items.map((suite) => (
              <ResourceCard
                key={suite.id}
                resource={suite}
                onClick={() =>
                  navigate({
                    to: `/suite/${suite.namespace}/${encodeURIComponent(suite.slug)}`,
                  })
                }
              />
            ))}
          </div>
          <Pagination
            page={page}
            totalPages={Math.max(1, Math.ceil(data.total / data.size))}
            onPageChange={setPage}
          />
        </>
      ) : (
        <EmptyState
          title={t("suite.emptyTitle")}
          description={t("suite.emptyDescription")}
        />
      )}
    </div>
  );
}

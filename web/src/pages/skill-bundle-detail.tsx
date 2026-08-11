import { useNavigate, useParams } from '@tanstack/react-router'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, ChevronDown, Download, FileText, Folder, Globe, Lock, Pencil, Sparkles, Tags, Users } from 'lucide-react'
import { Button, buttonVariants } from '@/shared/ui/button'
import { Card } from '@/shared/ui/card'
import { EmptyState } from '@/shared/components/empty-state'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { useSkillBundleDetail } from '@/shared/hooks/use-skill-bundle-queries'
import { APP_SHELL_PAGE_CLASS_NAME } from '@/app/page-shell-style'
import { skillBundleApi } from '@/api/client'
import { cn } from '@/shared/lib/utils'

function getExpertInitials(name: string) {
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((part) => part[0])
    .join('')
    .toUpperCase() || 'EP'
}

function formatDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleDateString()
}

function formatCompactCount(value?: number) {
  return new Intl.NumberFormat(undefined, { notation: 'compact', maximumFractionDigits: 1 }).format(value ?? 0)
}

function getStatusBadgeClass(status?: string) {
  return cn(
    'badge-soft',
    status === 'PUBLISHED' && 'badge-soft-green',
    status === 'ARCHIVED' && 'bg-secondary text-muted-foreground',
    status === 'DRAFT' && 'badge-soft-blue',
    status && !['PUBLISHED', 'ARCHIVED', 'DRAFT'].includes(status) && 'badge-soft-blue',
  )
}

function getVisibilityBadgeClass(visibility?: string) {
  return cn(
    'badge-soft inline-flex items-center gap-1',
    visibility === 'PUBLIC' && 'badge-soft-green',
    visibility === 'PRIVATE' && 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
    visibility === 'NAMESPACE_ONLY' && 'badge-soft-blue',
  )
}

function CapabilityItem({ title, children }: { title: string; children?: string | null }) {
  if (!children?.trim()) {
    return null
  }

  return (
    <div className="grid gap-2 border-b border-border/60 py-4 last:border-b-0 sm:grid-cols-[8rem,minmax(0,1fr)]">
      <h3 className="text-sm font-semibold text-foreground">{title}</h3>
      <p className="whitespace-pre-wrap text-sm leading-7 text-muted-foreground">{children}</p>
    </div>
  )
}

export function SkillBundleDetailPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [fileBrowserOpen, setFileBrowserOpen] = useState(true)
  const { namespace, slug } = useParams({ from: '/skill-bundles/$namespace/$slug' })
  const { data: bundle, isLoading, isError } = useSkillBundleDetail(namespace, slug)

  if (isLoading) {
    return (
      <div className={`${APP_SHELL_PAGE_CLASS_NAME} animate-fade-up`}>
        <div className="h-8 w-48 animate-shimmer rounded-lg" />
        <div className="h-24 animate-shimmer rounded-xl" />
        <div className="h-64 animate-shimmer rounded-xl" />
      </div>
    )
  }

  if (isError || !bundle) {
    return (
      <div className={APP_SHELL_PAGE_CLASS_NAME}>
        <EmptyState title={t('skillBundleDetail.notFoundTitle')} description={t('skillBundleDetail.notFoundDescription')} />
      </div>
    )
  }

  const initials = getExpertInitials(bundle.name)
  const expertiseLabels = bundle.labels ?? []
  const skillTotal = bundle.skillCount ?? bundle.items.length
  const packageFileCount = bundle.items.length

  return (
    <div className={`${APP_SHELL_PAGE_CLASS_NAME} mx-auto w-full max-w-[1440px]`}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => navigate({ to: '/skill-bundles', search: { q: '', sort: 'newest', page: 0 } })}
        >
          <ArrowLeft className="mr-2 h-4 w-4" />
          {t('skillBundleDetail.back')}
        </Button>
        <div className="flex flex-wrap items-center gap-2">
          <a
            className={buttonVariants({ variant: 'default', size: 'sm' })}
            href={skillBundleApi.getDownloadUrl(bundle.namespace, bundle.slug)}
          >
            <Download className="mr-2 h-4 w-4" />
            {t('skillBundleDetail.download')}
          </a>
          {bundle.canManage ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={() => navigate({ to: `/dashboard/skill-bundles/${bundle.namespace}/${encodeURIComponent(bundle.slug)}` })}
            >
              <Pencil className="mr-2 h-4 w-4" />
              {t('skillBundleDetail.edit')}
            </Button>
          ) : null}
        </div>
      </div>

      <header className="space-y-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start">
          <div
            aria-label={t('skillBundleDetail.avatarLabel')}
            className="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-brand-gradient text-lg font-semibold text-white shadow-sm"
          >
            {bundle.avatarUrl ? (
              <img src={bundle.avatarUrl} alt={bundle.name} className="h-full w-full object-cover" />
            ) : (
              initials
            )}
          </div>
          <div className="min-w-0 flex-1 space-y-4">
            <div className="flex flex-wrap items-center gap-2">
              <NamespaceBadge type="GLOBAL" name={`@${bundle.namespace}`} />
              {bundle.status ? (
                <span className={getStatusBadgeClass(bundle.status)}>
                  {t(`skillBundles.status.${bundle.status}`, { defaultValue: bundle.status })}
                </span>
              ) : null}
              {bundle.visibility ? (
                <span className={getVisibilityBadgeClass(bundle.visibility)}>
                  {bundle.visibility === 'PUBLIC' && <Globe className="h-3 w-3" />}
                  {bundle.visibility === 'PRIVATE' && <Lock className="h-3 w-3" />}
                  {bundle.visibility === 'NAMESPACE_ONLY' && <Users className="h-3 w-3" />}
                  {t(`skillBundles.visibility.${bundle.visibility}`, { defaultValue: bundle.visibility })}
                </span>
              ) : null}
            </div>
            <div className="space-y-3">
              <h1 className="text-3xl font-semibold font-heading leading-tight text-foreground sm:text-4xl">{bundle.name}</h1>
              {bundle.summary ? (
                <p className="max-w-3xl text-base leading-7 text-muted-foreground">{bundle.summary}</p>
              ) : null}
            </div>
          </div>
        </div>

        <div className="grid gap-3 rounded-2xl bg-secondary/60 p-4 sm:grid-cols-3">
          <div className="space-y-1 border-b border-border/70 pb-3 sm:border-b-0 sm:border-r sm:pb-0">
            <div className="text-xs text-muted-foreground">{t('skillBundleDetail.namespace')}</div>
            <div className="text-sm font-medium text-foreground">@{bundle.namespace}</div>
          </div>
          <div className="space-y-1 border-b border-border/70 pb-3 sm:border-b-0 sm:border-r sm:pb-0">
            <div className="text-xs text-muted-foreground">{t('skillBundleDetail.skillTotal')}</div>
            <div className="text-sm font-medium text-foreground">{t('skillBundleDetail.skillTotalValue', { skillCount: skillTotal })}</div>
          </div>
          <div className="space-y-1">
            <div className="text-xs text-muted-foreground">{t('skillBundleDetail.updatedAt')}</div>
            <div className="text-sm font-medium text-foreground">{formatDate(bundle.updatedAt)}</div>
          </div>
        </div>
      </header>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="space-y-8">
          <section className="space-y-3">
            <div className="flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-primary" />
              <h2 className="text-base font-semibold text-foreground">{t('skillBundleDetail.capabilityIntro')}</h2>
            </div>
            <div className="rounded-2xl bg-card px-5 py-2 shadow-sm ring-1 ring-border/70">
              <CapabilityItem title={t('skillBundleDetail.capabilityCanDo')}>{bundle.description}</CapabilityItem>
              <CapabilityItem title={t('skillBundleDetail.roleDescription')}>{bundle.roleDescription}</CapabilityItem>
              <CapabilityItem title={t('skillBundleDetail.applicableScenarios')}>{bundle.applicableScenarios}</CapabilityItem>
              <CapabilityItem title={t('skillBundleDetail.workingStyle')}>{bundle.methodology}</CapabilityItem>
              <CapabilityItem title={t('skillBundleDetail.recommendedSkillNotes')}>{bundle.recommendedSkillNotes}</CapabilityItem>
            </div>
          </section>

          <section className="space-y-3">
            <h2 className="text-base font-semibold text-foreground">{t('skillBundleDetail.expertise')}</h2>
            {expertiseLabels.length > 0 ? (
              <div className="flex flex-wrap gap-2">
                {expertiseLabels.map((label) => (
                  <span key={label.slug} className="rounded-full bg-secondary px-3 py-1.5 text-xs font-medium text-foreground">
                    {label.displayName}
                  </span>
                ))}
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">{t('skillBundleDetail.noExpertise')}</p>
            )}
          </section>
        </div>

        <aside className="space-y-5">
          <Card className="p-5 space-y-3">
            <button
              type="button"
              className="flex w-full min-h-11 items-center gap-2 text-left"
              aria-expanded={fileBrowserOpen}
              onClick={() => setFileBrowserOpen((value) => !value)}
            >
              <Folder className="h-4 w-4 text-muted-foreground" />
              <span className="text-sm font-semibold text-foreground">{t('skillBundleDetail.fileBrowser')}</span>
              <span className="ml-auto mr-2 text-xs text-muted-foreground">{packageFileCount}</span>
              <ChevronDown className={cn('h-4 w-4 text-muted-foreground transition-transform duration-200', fileBrowserOpen && 'rotate-180')} />
            </button>
            {fileBrowserOpen ? (
              <div className="space-y-3">
                {bundle.items.map((item) => (
                  <div key={`file-${item.skillId}`} className="space-y-2 rounded-xl bg-secondary/30 p-3">
                    <div className="flex min-w-0 items-center gap-2 text-sm font-mono text-foreground">
                      <Folder className="h-4 w-4 shrink-0 text-amber-500" />
                      <span className="truncate">{item.skillSlug}</span>
                    </div>
                    <div className="ml-6 flex min-w-0 items-center gap-2 text-sm font-mono text-muted-foreground">
                      <FileText className="h-4 w-4 shrink-0" />
                      <span className="truncate">SKILL.md</span>
                    </div>
                  </div>
                ))}
                {bundle.items.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{t('skillBundleDetail.noSkills')}</p>
                ) : null}
              </div>
            ) : null}
          </Card>

          <Card className="p-5 space-y-5">
            <div className="text-sm font-semibold text-foreground">{t('skillBundleDetail.packageInfo')}</div>
            <div className="flex items-center justify-between gap-4">
              <div className="text-sm text-muted-foreground">{t('skillBundleDetail.skillTotal')}</div>
              <div className="font-semibold text-foreground">{skillTotal}</div>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <div className="text-sm text-muted-foreground">{t('skillBundleDetail.downloads')}</div>
              <div className="font-semibold text-foreground">{formatCompactCount(bundle.downloadCount)}</div>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <div className="text-sm text-muted-foreground">{t('skillBundleDetail.rating')}</div>
              <div className="font-semibold text-foreground">{t('skillBundleDetail.ratingNone')}</div>
            </div>
            <div className="h-px bg-border/40" />
            <div className="flex items-center justify-between gap-4">
              <div className="text-sm text-muted-foreground">{t('skillBundleDetail.namespace')}</div>
              <NamespaceBadge type="GLOBAL" name={bundle.namespace} />
            </div>
            <div className="h-px bg-border/40" />
            <div className="space-y-3">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Tags className="h-4 w-4" />
                <span>{t('skillBundleDetail.tags')}</span>
              </div>
              {expertiseLabels.length > 0 ? (
                <div className="flex flex-wrap gap-2">
                  {expertiseLabels.map((label) => (
                    <span key={`sidebar-${label.slug}`} className="rounded-full border border-slate-300 bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-800">
                      {label.displayName}
                    </span>
                  ))}
                </div>
              ) : (
                <p className="text-sm text-muted-foreground">{t('skillBundleDetail.noExpertise')}</p>
              )}
            </div>
          </Card>

          <h2 className="text-base font-semibold text-foreground">{t('skillBundleDetail.skills')}</h2>
          {bundle.items.length > 0 ? (
            <div className="space-y-3">
              {bundle.items.map((item, index) => (
                <Card key={`${item.skillId}-${index}`} className="p-4">
                  <button
                    type="button"
                    className="block min-h-11 w-full text-left"
                    onClick={() => navigate({ to: `/space/${item.namespace}/${encodeURIComponent(item.skillSlug)}` })}
                  >
                    <div className="text-sm font-semibold text-foreground">{item.displayName}</div>
                    <div className="mt-1 text-xs text-muted-foreground">@{item.namespace}/{item.skillSlug}</div>
                    {item.note ? (
                      <p className="mt-3 text-sm leading-6 text-muted-foreground">{item.note}</p>
                    ) : null}
                  </button>
                </Card>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">{t('skillBundleDetail.noSkills')}</p>
          )}
        </aside>
      </div>
    </div>
  )
}

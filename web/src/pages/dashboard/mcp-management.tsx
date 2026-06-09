import { ExternalLink, Info } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'

const DEFAULT_CONTEXT_FORGE_ADMIN_URL = '/contextforge/admin/login'

function getContextForgeAdminUrl(): string {
  if (typeof window === 'undefined') {
    return DEFAULT_CONTEXT_FORGE_ADMIN_URL
  }

  return window.__SKILLHUB_RUNTIME_CONFIG__?.mcpContextForgeAdminUrl?.trim() || DEFAULT_CONTEXT_FORGE_ADMIN_URL
}

export function McpManagementPage() {
  const { t } = useTranslation()
  const contextForgeAdminUrl = getContextForgeAdminUrl()

  return (
    <div className="space-y-6 animate-fade-up">
      <DashboardPageHeader
        title={t('mcpManagement.title')}
        subtitle={t('mcpManagement.subtitle')}
      />
      <div className="flex flex-col gap-3 rounded-lg border border-primary/20 bg-primary/5 p-4 text-sm sm:flex-row sm:items-center sm:justify-between">
        <div className="flex min-w-0 gap-3">
          <Info className="mt-0.5 h-4 w-4 flex-none text-primary" aria-hidden="true" />
          <p className="leading-6 text-muted-foreground">{t('mcpManagement.loginHint')}</p>
        </div>
        <a
          href={contextForgeAdminUrl}
          target="_blank"
          rel="noreferrer"
          className="inline-flex h-9 shrink-0 items-center justify-center gap-2 rounded-md border border-border bg-background px-3 text-sm font-medium text-foreground transition-colors hover:bg-secondary"
        >
          <ExternalLink className="h-4 w-4" aria-hidden="true" />
          {t('mcpManagement.openInNewTab')}
        </a>
      </div>
      <section className="overflow-hidden rounded-lg border bg-card shadow-sm">
        <iframe
          src={contextForgeAdminUrl}
          title={t('mcpManagement.iframeTitle')}
          className="block h-[calc(100dvh-14rem)] min-h-[640px] w-full border-0 bg-background"
        />
      </section>
    </div>
  )
}

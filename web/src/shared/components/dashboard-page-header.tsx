interface DashboardPageHeaderProps {
  title: string
  subtitle?: string
  actions?: React.ReactNode
}

/**
 * Standard header used by dashboard sub-pages so navigation and page framing stay consistent.
 *
 * The "back to dashboard" link is intentionally omitted — the sidebar already provides
 * complete navigation and makes a dedicated back link redundant.
 */
export function DashboardPageHeader({ title, subtitle, actions }: DashboardPageHeaderProps) {
  return (
    <div>
      <div className="flex items-center justify-between gap-4">
        <div>
          <h1 className="text-4xl font-semibold tracking-tight text-foreground" style={{ marginBottom:'0.5rem' }}>{title}</h1>
          {subtitle ? <p className="mt-1 text-lg text-muted-foreground" >{subtitle}</p> : null}
        </div>
        {actions}
      </div>
    </div>
  )
}

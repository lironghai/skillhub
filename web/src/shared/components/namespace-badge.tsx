import { cn } from '@/shared/lib/utils'

interface NamespaceBadgeProps {
  type: 'GLOBAL' | 'TEAM'
  name: string
  className?: string
  title?: string
}

export function NamespaceBadge({ type, name, className, title }: NamespaceBadgeProps) {
  return (
    <span
      title={title}
      className={cn(
        'inline-flex items-center rounded-full border px-3 py-1 text-xs font-medium transition-colors',
        type === 'GLOBAL'
          ? 'bg-emerald-500/10 text-emerald-700 dark:text-emerald-400 border-emerald-500/20'
          : 'border-[rgba(237,108,48,0.2)] bg-[rgba(237,108,48,0.1)] text-[var(--brand-end)]',
        className
      )}
    >
      {name}
    </span>
  )
}

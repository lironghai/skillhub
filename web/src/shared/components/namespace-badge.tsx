import { cn } from '@/shared/lib/utils'

interface NamespaceBadgeProps {
  type: 'GLOBAL' | 'TEAM'
  name: string
  className?: string
}

export function NamespaceBadge({ name, className }: NamespaceBadgeProps) {
  return (
    // type === "GLOBAL"
    //   ? "bg-emerald-500/10 text-emerald-400 border-emerald-500/20 hover:bg-emerald-500/15"
    //   : "bg-accent/1 text-accent border-accent/20 hljs-tag-bg",
    <span
      className={cn(
        "inline-flex items-center rounded-full px-3 py-1 text-xs font-medium transition-colors bg-accent/1 text-accent border-accent/20 hljs-tag-bg",
        className,
      )}
    >
      {name}
    </span>
  );
}

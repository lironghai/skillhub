interface SvgIconProps {
  name: string
  className?: string
}

export function SvgIcon({ name, className }: SvgIconProps) {
  return (
    <svg className={className} aria-hidden="true">
      <use href={`#${name}`} />
    </svg>
  )
}

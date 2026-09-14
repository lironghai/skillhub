interface SvgIconProps {
  name: string
  className?: string
  style?: React.CSSProperties
}

export function SvgIcon({ name, className, style }: SvgIconProps) {
  return (
    <svg className={className} style={style} aria-hidden="true">
      <use href={`#${name}`} />
    </svg>
  )
}

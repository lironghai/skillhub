interface SvgIconProps {
  name: string
  className?: string
  style?: React.CSSProperties
  role?: string
  'aria-label'?: string
}

export function SvgIcon({ name, className, style, role, 'aria-label': ariaLabel }: SvgIconProps) {
  return (
    <svg className={className} style={style} role={role} aria-label={ariaLabel} aria-hidden={ariaLabel ? undefined : true}>
      <use href={`#${name}`} />
    </svg>
  )
}

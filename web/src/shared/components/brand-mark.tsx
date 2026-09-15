import { cn } from '@/shared/lib/utils'
import { SvgIcon } from './svg-icon'

interface BrandMarkProps {
  className?: string
  imageClassName?: string
  alt?: string
}

/**
 * 统一展示内部 HERO Skillhub 品牌图标。
 */
export function BrandMark({ className, imageClassName, alt = 'SkillHub' }: BrandMarkProps) {
  return (
    <span className={cn('inline-flex items-center justify-center overflow-hidden rounded-xl', className)}>
      <SvgIcon
        name="svg-login_logo"
        role="img"
        aria-label={alt}
        className={cn('h-full w-full object-contain', imageClassName)}
      />
    </span>
  )
}

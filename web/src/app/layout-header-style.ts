import { cn } from '@/shared/lib/utils'

export const APP_HEADER_BASE_CLASS_NAME =
  'sticky top-0 z-50 flex h-[68px] items-center justify-between gap-3 bg-white px-6 transition-shadow duration-200 md:px-20 shadow-[0_2px_4px_rgba(0,0,0,0.05)]'

export const APP_HEADER_ELEVATED_CLASS_NAME = 'shadow-[0_10px_24px_-20px_rgba(15,23,42,0.32)]'

export function getAppHeaderClassName(isElevated: boolean): string {
  return cn(APP_HEADER_BASE_CLASS_NAME, isElevated && APP_HEADER_ELEVATED_CLASS_NAME)
}

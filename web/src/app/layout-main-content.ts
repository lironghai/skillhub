export const LANDING_MAIN_CLASS_NAME = 'flex-1 relative z-10'
export const DEFAULT_MAIN_CLASS_NAME = 'flex-1 relative z-10 px-6 py-10 md:px-12'
export const CENTERED_MAIN_CLASS_NAME = 'flex-1 relative z-10 px-4 py-8 sm:px-6 md:px-12 md:py-10'
export const COMMON_TOP_MAIN_CLASS_NAME = `${CENTERED_MAIN_CLASS_NAME} common-top-bg`
export const LOGIN_MAIN_CLASS_NAME = `${CENTERED_MAIN_CLASS_NAME} login-main-bg`
export const CENTERED_SEARCH_CONTENT_CLASS_NAME = 'mx-auto w-full max-w-[1200px]'
export const CENTERED_DASHBOARD_CONTENT_CLASS_NAME = 'mx-auto min-h-[calc(100vh-11rem)] w-full max-w-[1100px]'

export const DASHBOARD_PATH_PREFIXES = ['/dashboard', '/settings/'] as const

const COMMON_TOP_MAIN_PATHS = new Set([
  '/search',
  '/suites',
  '/dashboard',
  '/dashboard/publish',
  '/dashboard/mcp',
])

const COMMON_TOP_MAIN_PATH_PATTERNS = [
  /^\/space\/[^/]+\/[^/]+$/,
]

interface AppMainContentLayout {
  mainClassName: string
  contentClassName: string
}

export function resolveAppMainContentPathname(
  pathname: string,
  resolvedPathname?: string,
): string {
  return resolvedPathname ?? pathname
}

function isCommonTopMainPath(pathname: string): boolean {
  return COMMON_TOP_MAIN_PATHS.has(pathname)
    || COMMON_TOP_MAIN_PATH_PATTERNS.some((pattern) => pattern.test(pathname))
}

export function getAppMainContentLayout(pathname: string): AppMainContentLayout {
  if (pathname === '/') {
    return {
      mainClassName: LANDING_MAIN_CLASS_NAME,
      contentClassName: '',
    }
  }

  if (isCommonTopMainPath(pathname)) {
    return {
      mainClassName: COMMON_TOP_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    }
  }

  if (pathname === '/login') {
    return {
      mainClassName: LOGIN_MAIN_CLASS_NAME,
      contentClassName: '',
    }
  }

  if (pathname === '/search' || pathname.startsWith('/suites') || pathname === '/dashboard' || pathname.startsWith('/dashboard/') || pathname.startsWith('/settings/')) {
    return {
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    }
  }

  // if (pathname === '/dashboard' || pathname.startsWith('/dashboard/') || pathname.startsWith('/settings/')) {
  //   return {
  //     mainClassName: CENTERED_MAIN_CLASS_NAME,
  //     contentClassName: CENTERED_DASHBOARD_CONTENT_CLASS_NAME,
  //   }
  // }

  return {
    mainClassName: DEFAULT_MAIN_CLASS_NAME,
    contentClassName: '',
  }
}

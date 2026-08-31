import { Suspense, useEffect, useRef, useState } from 'react'
import { Outlet, Link, useRouterState } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useAuth } from '@/features/auth/use-auth'
import { LanguageSwitcher } from '@/shared/components/language-switcher'
import { UserMenu } from '@/shared/components/user-menu'
import { NotificationBell } from '@/features/notification/notification-bell'
import { dismissOpenOverlays } from '@/shared/lib/dismiss-open-overlays'
import { syncDocumentLanguage } from '@/shared/lib/document-language'
import { getAppHeaderClassName } from './layout-header-style'
import { getAppMainContentLayout, resolveAppMainContentPathname } from './layout-main-content'
import { SvgIcon } from '@/shared/components/svg-icon'
import { Menu, X } from 'lucide-react'
import footerLogo from '@/assets/footer_logo.png'

/**
 * Application shell shared by all routed pages.
 *
 * It owns the global header, footer, language switcher, auth-aware navigation, and suspense
 * fallback used while lazy route modules are loading.
 */
export function Layout() {
  const { t, i18n } = useTranslation()
  const { pathname, resolvedPathname } = useRouterState({
    select: (s) => ({
      pathname: s.location.pathname,
      resolvedPathname: s.resolvedLocation?.pathname,
    }),
  })
  const { user, isLoading } = useAuth()
  const [isHeaderElevated, setIsHeaderElevated] = useState(false)
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false)
  const mobileNavRef = useRef<HTMLDivElement | null>(null)
  const previousPathnameRef = useRef(pathname)
  const contentLayoutPathname = resolveAppMainContentPathname(pathname, resolvedPathname)
  const mainContentLayout = getAppMainContentLayout(contentLayoutPathname)
  const showFooter = contentLayoutPathname !== '/dashboard/workbench'

  useEffect(() => {
    syncDocumentLanguage(i18n.resolvedLanguage ?? i18n.language)
  }, [i18n.language, i18n.resolvedLanguage])

  useEffect(() => {
    const updateHeaderElevation = () => {
      setIsHeaderElevated(window.scrollY > 0)
    }

    updateHeaderElevation()
    window.addEventListener('scroll', updateHeaderElevation, { passive: true })

    return () => {
      window.removeEventListener('scroll', updateHeaderElevation)
    }
  }, [])

  // Pathname-only: search debounce on /search must not dismiss overlays mid-typing.
  useEffect(() => {
    if (previousPathnameRef.current === pathname) {
      return
    }
    previousPathnameRef.current = pathname
    setIsMobileNavOpen(false)
    dismissOpenOverlays()
  }, [pathname])

  useEffect(() => {
    if (!isMobileNavOpen) return

    const closeOnOutsideClick = (event: MouseEvent) => {
      if (!mobileNavRef.current?.contains(event.target as Node)) {
        setIsMobileNavOpen(false)
      }
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setIsMobileNavOpen(false)
    }

    document.addEventListener('mousedown', closeOnOutsideClick)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('mousedown', closeOnOutsideClick)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isMobileNavOpen])

  const navItems: Array<{
    label: string
    to: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', exact: true },
    { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
    { label: t('nav.workbench'), to: '/dashboard/workbench', auth: true },
    { label: t('nav.search'), to: '/search' },
    { label: t('nav.skillBundles'), to: '/skill-bundles' },
    { label: t('nav.mcpManagement'), to: '/dashboard/mcp' },
    { label: t('nav.dashboard'), to: '/dashboard', auth: true },
    { label: t('nav.mySkills'), to: '/dashboard/skills', auth: true },
  ]

  const isActive = (to: string, exact?: boolean) => {
    if (exact) return pathname === to
    // Keep matching strict so parent dashboard paths do not highlight unrelated child links.
    return pathname === to
  }

  return (
    <div className="min-h-screen flex flex-col relative" style={{ background: 'var(--bg-page, hsl(var(--background)))' }}>
      {/* Header */}
      <header
        className={getAppHeaderClassName(isHeaderElevated)}
        style={{ borderColor: "hsl(var(--border))" }}
      >
        <Link to="/" className="inline-flex items-center">
          <SvgIcon name="svg-text-HeroSkillhub" className="h-4 w-auto" />
        </Link>

        <nav className="hidden xl:flex items-center gap-4 2xl:gap-7 text-[15px] font-medium h-full">
          {navItems.map((item) => {
            if (item.auth && !user) return null;
            const active = isActive(item.to, item.exact);

            return (
              <Link
                key={item.to}
                to={item.to}
                className={
                  active
                    ? "flex items-center h-full text-[#bf3732] border-b-[3px] border-[#bf3732] font-bold"
                    : "flex items-center h-full text-[#666] hover:text-[#bf3732] transition-colors duration-150"
                }
              >
                {item.label}
              </Link>
            );
          })}
        </nav>

        <div ref={mobileNavRef} className="relative xl:hidden">
          <button
            type="button"
            aria-label="Navigation menu"
            aria-expanded={isMobileNavOpen}
            aria-controls="mobile-navigation"
            title="Navigation menu"
            className="flex h-9 w-9 items-center justify-center rounded-md text-[#555] transition-colors hover:bg-[#f5f5f5] hover:text-[#bf3732] focus:outline-none focus-visible:ring-2 focus-visible:ring-[#bf3732]"
            onClick={() => setIsMobileNavOpen((current) => !current)}
          >
            {isMobileNavOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
          </button>
          <nav
            id="mobile-navigation"
            data-mobile-navigation
            aria-hidden={!isMobileNavOpen}
            className={`${isMobileNavOpen ? 'flex' : 'hidden'} fixed left-3 right-3 top-[60px] z-50 max-h-[calc(100vh-72px)] flex-col overflow-y-auto rounded-md border bg-white p-2 shadow-lg sm:absolute sm:left-auto sm:right-0 sm:top-full sm:w-64`}
          >
            {navItems.map((item) => {
              if (item.auth && !user) return null
              const active = isActive(item.to, item.exact)

              return (
                <Link
                  key={item.to}
                  to={item.to}
                  aria-current={active ? 'page' : undefined}
                  className={
                    active
                      ? 'rounded-sm bg-[#bf3732]/10 px-3 py-2.5 text-sm font-semibold text-[#bf3732]'
                      : 'rounded-sm px-3 py-2.5 text-sm font-medium text-[#555] transition-colors hover:bg-[#f5f5f5] hover:text-[#bf3732]'
                  }
                  onClick={() => setIsMobileNavOpen(false)}
                >
                  {item.label}
                </Link>
              )
            })}
          </nav>
        </div>

        <div className="flex items-center gap-2 text-[15px] font-normal sm:gap-4">
          <LanguageSwitcher />
          <span className="hidden text-[#ccc] select-none sm:inline">|</span>
          {user && <NotificationBell />}
          {isLoading ? null : user ? (
            <UserMenu user={user} triggerClassName="max-w-[9rem]" />
          ) : (
            <Link
              to="/login"
              search={{ returnTo: "" }}
              className="flex items-center justify-center min-w-[52px] px-3 h-[32px] bg-white border border-[#ccc] rounded-[8px] text-sm text-[#333] hover:border-[#bf3732] hover:text-[#bf3732] transition-colors duration-150"
            >
              {t("nav.login")}
            </Link>
          )}
        </div>
      </header>

      {/* Main content */}
      <main className={mainContentLayout.mainClassName}>
        <Suspense
          fallback={
            <div className="space-y-4 animate-fade-up">
              <div className="h-10 w-48 animate-shimmer rounded-lg" />
              <div className="h-5 w-72 animate-shimmer rounded-md" />
              <div className="h-64 animate-shimmer rounded-xl" />
            </div>
          }
        >
          <div className={mainContentLayout.contentClassName}>
            <Outlet />
          </div>
        </Suspense>
      </main>

      {/* Footer */}
      {showFooter && (
      <footer
        className="relative z-10 border-t mt-auto"
        style={{ background: "#fff", borderColor: "hsl(var(--border))" }}
      >
        <div className="max-w-6xl mx-auto px-6 md:px-12 py-10">
          <div className="flex flex-col md:flex-row md:items-start md:justify-between gap-10 md:gap-12">
            <div className="flex-shrink-0">
              <div className="flex items-center gap-2 mb-3">
                <img
                  src={footerLogo}
                  alt="HeroSkillHub"
                  className="h-9 w-auto"
                />
              </div>
              <p
                className="text-sm max-w-xs"
                style={{ color: "hsl(var(--text-secondary))" }}
              >
                {t("layout.footerDescription")}
              </p>
            </div>
            <div className="flex flex-wrap gap-12 md:gap-16">
              <div>
                <h4
                  className="text-sm font-semibold mb-3"
                  style={{ color: "hsl(var(--foreground))" }}
                >
                  {t("nav.home")}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <Link
                      to="/"
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("nav.home")}
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/search"
                      search={{
                        q: "",
                        sort: "relevance",
                        page: 0,
                        starredOnly: false,
                      }}
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("nav.search")}
                    </Link>
                  </li>
                  <li>
                    <Link
                      to="/dashboard"
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("nav.dashboard")}
                    </Link>
                  </li>
                </ul>
              </div>
              <div>
                <h4
                  className="text-sm font-semibold mb-3"
                  style={{ color: "hsl(var(--foreground))" }}
                >
                  {t("footer.resources")}
                </h4>
                <ul className="space-y-2 text-sm">
                  <li>
                    <a
                      href="https://iflytek.github.io/skillhub/"
                      target="_blank"
                      rel="noreferrer"
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("footer.docs")}
                    </a>
                  </li>
                  <li>
                    <a
                      href="https://github.com/iflytek/skillhub/blob/main/docs/06-api-design.md"
                      target="_blank"
                      rel="noreferrer"
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("footer.api")}
                    </a>
                  </li>
                  <li>
                    <a
                      href="https://github.com/iflytek/skillhub/discussions"
                      target="_blank"
                      rel="noreferrer"
                      className="hover:opacity-80 transition-opacity"
                      style={{ color: "hsl(var(--text-secondary))" }}
                    >
                      {t("footer.community")}
                    </a>
                  </li>
                </ul>
              </div>
            </div>
          </div>
          <div
            className="mt-10 pt-6 border-t flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 text-xs"
            style={{
              borderColor: "hsl(var(--border))",
              color: "hsl(var(--muted-foreground))",
            }}
          >
            <span>{t("footer.copyright")}</span>
            <div className="flex items-center gap-2">
              <Link
                to="/privacy"
                className="hover:opacity-80 transition-opacity"
              >
                {t("footer.privacy")}
              </Link>
              <span>|</span>
              <Link to="/terms" className="hover:opacity-80 transition-opacity">
                {t("footer.terms")}
              </Link>
            </div>
          </div>
        </div>
      </footer>
      )}
    </div>
  );
}

import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import type { ReactNode } from 'react'
import { describe, expect, it, vi } from 'vitest'

// Layout is a component-only file with no exported pure functions or constants.
// We verify that the named export exists for the router to consume.

vi.mock('@tanstack/react-router', () => ({
  Outlet: () => null,
  Link: ({ children, to }: { children: ReactNode; to: string }) =>
    createElement('a', { href: to }, children),
  useRouterState: () => ({ pathname: '/', resolvedPathname: '/' }),
}))

vi.mock('react-i18next', async () => {
  const actual = await vi.importActual<typeof import('react-i18next')>('react-i18next')
  return {
    ...actual,
    useTranslation: () => ({
      t: (key: string) => key,
      i18n: { language: 'en' },
    }),
  }
})

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({
    user: null,
    isLoading: false,
  }),
}))

vi.mock('@/shared/components/language-switcher', () => ({
  LanguageSwitcher: () => null,
}))

vi.mock('@/shared/components/user-menu', () => ({
  UserMenu: () => null,
}))

vi.mock('./layout-header-style', () => ({
  getAppHeaderClassName: () => 'header-class',
}))

vi.mock('./layout-main-content', () => ({
  resolveAppMainContentPathname: (p: string) => p,
  getAppMainContentLayout: () => ({
    mainClassName: 'main-class',
    contentClassName: 'content-class',
  }),
}))

import { Layout } from './layout'

describe('Layout', () => {
  it('exports a named Layout component function', () => {
    expect(typeof Layout).toBe('function')
    expect(Layout.name).toBe('Layout')
  })

  it('shows the MCP management entry before SkillHub login', () => {
    const html = renderToStaticMarkup(createElement(Layout))

    expect(html).toContain('nav.mcpManagement')
  })

  it('shows the skill bundles entry before SkillHub login', () => {
    const html = renderToStaticMarkup(createElement(Layout))

    expect(html).toContain('nav.skillBundles')
  })

  it('keeps full navigation discoverable through a responsive menu', () => {
    const html = renderToStaticMarkup(createElement(Layout))

    expect(html).toContain('aria-label="Navigation menu"')
    expect(html).toContain('data-mobile-navigation="true"')
    expect(html).toContain('xl:hidden')
    expect(html).toContain('hidden xl:flex')
    expect(html).toContain('text-[15px] font-medium')
    expect(html).not.toContain('text-[14px] font-medium')
    expect(html).toContain('href="/search"')
    expect(html).toContain('href="/skill-bundles"')
    expect(html).toContain('href="/dashboard/mcp"')
  })

  it('renders the footer resources column', () => {
    const html = renderToStaticMarkup(createElement(Layout))

    expect(html).toContain('footer.resources')
    expect(html).toContain('footer.docs')
    expect(html).toContain('footer.api')
    expect(html).toContain('footer.community')
    expect(html).toContain('href="https://iflytek.github.io/skillhub/"')
    expect(html).toContain('href="https://github.com/iflytek/skillhub/blob/main/docs/06-api-design.md"')
    expect(html).toContain('href="https://github.com/iflytek/skillhub/discussions"')
    expect(html).not.toContain('href="/docs"')
    expect(html).not.toContain('href="/api"')
    expect(html).not.toContain('href="/community"')
  })
})

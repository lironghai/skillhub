import { describe, expect, it } from 'vitest'
import {
  CENTERED_MAIN_CLASS_NAME,
  CENTERED_SEARCH_CONTENT_CLASS_NAME,
  COMMON_TOP_MAIN_CLASS_NAME,
  DEFAULT_MAIN_CLASS_NAME,
  getAppMainContentLayout,
  resolveAppMainContentPathname,
} from './layout-main-content'

describe('getAppMainContentLayout', () => {
  it('keeps the landing page full width without the app-shell padding wrapper', () => {
    expect(getAppMainContentLayout('/')).toEqual({
      mainClassName: 'flex-1 relative z-10',
      contentClassName: '',
    })
  })

  it('centers the search page content with roomier responsive gutters', () => {
    const layout = getAppMainContentLayout('/search')

    expect(layout).toEqual({
      mainClassName: COMMON_TOP_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    })
    expect(layout.contentClassName).toContain('max-w-[1200px]')
  })

  it('centers dashboard sub-pages within the standard content frame', () => {
    const layout = getAppMainContentLayout('/dashboard/skills')

    expect(layout).toEqual({
      mainClassName: CENTERED_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    })
    expect(layout.contentClassName).toContain('max-w-[1200px]')
  })

  it('uses the common top layout for a concrete skill detail route', () => {
    expect(getAppMainContentLayout('/space/acme/demo')).toEqual({
      mainClassName: COMMON_TOP_MAIN_CLASS_NAME,
      contentClassName: CENTERED_SEARCH_CONTENT_CLASS_NAME,
    })
  })

  it('does not apply the skill detail top background to adjacent space routes', () => {
    expect(getAppMainContentLayout('/space/acme')).toEqual({
      mainClassName: DEFAULT_MAIN_CLASS_NAME,
      contentClassName: '',
    })
    expect(getAppMainContentLayout('/space/acme/demo/compare')).toEqual({
      mainClassName: DEFAULT_MAIN_CLASS_NAME,
      contentClassName: '',
    })
  })
})

describe('resolveAppMainContentPathname', () => {
  it('sticks with the last resolved route while the next page is still pending', () => {
    expect(resolveAppMainContentPathname('/dashboard', '/search')).toBe('/search')
  })

  it('falls back to the live location when no resolved route is available yet', () => {
    expect(resolveAppMainContentPathname('/search')).toBe('/search')
  })
})

import { describe, expect, it } from 'vitest'
import viteConfig, { rewriteContextForgeRedirectLocation } from './vite.config'

describe('vite dev proxy', () => {
  it('forwards the embedded ContextForge path to the local gateway with the embedded prefix intact', () => {
    const proxy = viteConfig.server?.proxy as Record<string, { target: string, rewrite?: (path: string) => string }>
    const contextForgeProxy = proxy['/contextforge']

    expect(contextForgeProxy.target).toBe(process.env.VITE_CONTEXT_FORGE_DEV_ORIGIN ?? 'http://localhost:4444')
    expect(contextForgeProxy.rewrite).toBeUndefined()
  })

  it('keeps ContextForge admin redirects under the embedded ContextForge path', () => {
    expect(rewriteContextForgeRedirectLocation('/admin/')).toBe('/contextforge/admin/')
    expect(rewriteContextForgeRedirectLocation('/admin/tools')).toBe('/contextforge/admin/tools')
    expect(rewriteContextForgeRedirectLocation('http://localhost:3000/admin/')).toBe('/contextforge/admin/')
    expect(rewriteContextForgeRedirectLocation('https://contextforge.example.com/admin/tools')).toBe('/contextforge/admin/tools')
    expect(rewriteContextForgeRedirectLocation('/contextforge/admin/')).toBe('/contextforge/admin/')
    expect(rewriteContextForgeRedirectLocation('/api/admin/')).toBe('/api/admin/')
  })
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import type { IncomingMessage } from 'node:http'

const LEGACY_BROWSER_TARGETS = ['chrome83', 'edge83', 'firefox78', 'safari14']
const CONTEXT_FORGE_DEV_ORIGIN = process.env.VITE_CONTEXT_FORGE_DEV_ORIGIN ?? 'http://localhost:4444'
const CONTEXT_FORGE_EMBEDDED_PREFIX = '/contextforge'
const CONTEXT_FORGE_ADMIN_REDIRECT_PATTERN = /^(?<origin>https?:\/\/[^/]+)?\/admin(?<suffix>\/.*|$)/

export function rewriteContextForgeRedirectLocation(location: string): string {
  const match = location.match(CONTEXT_FORGE_ADMIN_REDIRECT_PATTERN)

  if (!match?.groups) {
    return location
  }

  const origin = match.groups.origin ?? ''
  const suffix = match.groups.suffix ?? ''

  return `${origin}${CONTEXT_FORGE_EMBEDDED_PREFIX}/admin${suffix}`
}

function rewriteContextForgeProxyRedirect(proxyRes: IncomingMessage): void {
  const location = proxyRes.headers.location

  if (Array.isArray(location)) {
    proxyRes.headers.location = location.map(rewriteContextForgeRedirectLocation)
    return
  }

  if (typeof location === 'string') {
    proxyRes.headers.location = rewriteContextForgeRedirectLocation(location)
  }
}

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: LEGACY_BROWSER_TARGETS,
    cssTarget: LEGACY_BROWSER_TARGETS,
  },
  optimizeDeps: {
    esbuildOptions: {
      target: LEGACY_BROWSER_TARGETS,
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
  },
  server: {
    port: 3000,
    watch: {
      usePolling: true,
      interval: 150,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      [CONTEXT_FORGE_EMBEDDED_PREFIX]: {
        target: CONTEXT_FORGE_DEV_ORIGIN,
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyRes', rewriteContextForgeProxyRedirect)
        },
      },
    },
  },
})

import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'
import path from 'path'
import type { IncomingMessage } from 'node:http'

const JS_BUILD_TARGET = 'es2020'
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
  plugins: [react(), createSvgIconsPlugin({
    iconDirs: [path.resolve(__dirname, 'src/assets/svg')],
    symbolId: 'svg-[name]',
  })],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  build: {
    target: JS_BUILD_TARGET,
    cssTarget: LEGACY_BROWSER_TARGETS,
  },
  optimizeDeps: {
    esbuildOptions: {
      target: JS_BUILD_TARGET,
    },
  },
  test: {
    exclude: ['**/node_modules/**', '**/e2e/**'],
    testTimeout: 30000,
    hookTimeout: 30000,
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

import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

describe('web Docker entrypoint scripts', () => {
  it('uses LF line endings so Alpine can execute the runtime config script', () => {
    const script = readFileSync('docker-entrypoint.d/30-runtime-config.sh', 'utf8')

    expect(script).not.toContain('\r\n')
    expect(script.startsWith('#!/bin/sh\n')).toBe(true)
  })

  it('exports runtime defaults before envsubst generates runtime-config.js', () => {
    const script = readFileSync('docker-entrypoint.d/30-runtime-config.sh', 'utf8')

    expect(script).toContain(': "${SKILLHUB_WEB_API_BASE_URL:=}"')
    expect(script).toContain('export \\\n')
    expect(script).toContain('  SKILLHUB_WEB_API_BASE_URL \\')
  })

  it('keeps the ContextForge UI behind the MCP master switch', () => {
    const script = readFileSync('docker-entrypoint.d/30-runtime-config.sh', 'utf8')

    expect(script).toContain('if [ "$SKILLHUB_MCP_ENABLED" != "true" ]; then')
    expect(script).toContain('SKILLHUB_WEB_MCP_CONTEXT_FORGE_ENABLED=false')
  })

  it('proxies Swagger UI and OpenAPI through the backend with deployment prefix headers', () => {
    const config = readFileSync('nginx.conf.template', 'utf8')

    expect(config).toMatch(/location \/swagger-ui\/ \{[\s\S]*?X-Forwarded-Prefix \$skillhub_forwarded_prefix;/)
    expect(config).toMatch(/location \/v3\/api-docs \{[\s\S]*?X-Forwarded-Prefix \$skillhub_forwarded_prefix;/)
  })

  it('preserves the ContextForge APP_ROOT_PATH prefix', () => {
    const config = readFileSync('nginx.conf.template', 'utf8')

    expect(config).toMatch(/location \/contextforge\/ \{[\s\S]*?proxy_pass \$\{SKILLHUB_CONTEXT_FORGE_UPSTREAM\};/)
    expect(config).toMatch(/location \/contextforge\/ \{[\s\S]*?X-Forwarded-Prefix \/contextforge;/)
  })
})

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

    expect(script).toContain(': "${SKILLHUB_CONTEXT_FORGE_ADMIN_URL:=/contextforge/admin/login}"')
    expect(script).toContain('export \\\n')
    expect(script).toContain('  SKILLHUB_CONTEXT_FORGE_ADMIN_URL \\')
  })
})

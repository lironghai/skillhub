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

    expect(script).toContain(': "${SKILLHUB_WEB_AUTH_DIRECT_ENABLED:=false}"')
    expect(script).toContain(': "${SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED:=false}"')
    expect(script).toContain('export \\\n')
    expect(script).toContain('  SKILLHUB_WEB_AUTH_DIRECT_ENABLED \\')
    expect(script).toContain('  SKILLHUB_WEB_AUTH_SESSION_BOOTSTRAP_ENABLED \\')
  })
})

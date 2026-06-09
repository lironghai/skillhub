import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const skillhubManifest = readFileSync(new URL('../../skillhub.yaml', import.meta.url), 'utf8')

function getManifestBlock(resourceName: string): string {
  return skillhubManifest
    .split(/\n---\n/)
    .find((block) => block.includes(`name: ${resourceName}`)) ?? ''
}

describe('skillhub MCP ingress manifest', () => {
  it('routes ContextForge UI through a prefix that does not conflict with ContextForge /mcp transport', () => {
    const ingressBlock = getManifestBlock('skillhub-mcp-context-forge')

    expect(ingressBlock).toContain('path: /contextforge(/|$)(.*)')
    expect(ingressBlock).not.toContain('nginx.ingress.kubernetes.io/rewrite-target')
    expect(ingressBlock).not.toContain('path: /mcp(/|$)(.*)')
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const originalWindow = globalThis.window
const originalDocument = globalThis.document

function setMockWindow(runtimeConfig?: Window['__SKILLHUB_RUNTIME_CONFIG__']) {
  Object.defineProperty(globalThis, 'window', {
    configurable: true,
    writable: true,
    value: {
      __SKILLHUB_RUNTIME_CONFIG__: runtimeConfig,
    } satisfies Pick<Window, '__SKILLHUB_RUNTIME_CONFIG__'>,
  })
}

// Mock i18n before importing client
vi.mock('@/i18n/config', () => ({
  default: { resolvedLanguage: 'en' },
}))

// Mock api-error before importing client
vi.mock('@/shared/lib/api-error', () => ({
  ApiError: class ApiError extends Error {
    status: number
    serverMessage?: string
    serverMessageKey?: string
    constructor(message: string, status: number, serverMessage?: string, serverMessageKey?: string) {
      super(message)
      this.status = status
      this.serverMessage = serverMessage
      this.serverMessageKey = serverMessageKey
    }
  },
  handleApiError: vi.fn(),
}))

import {
  WEB_API_PREFIX,
  buildApiUrl,
  fetchText,
  getDirectAuthRuntimeConfig,
  getSessionBootstrapRuntimeConfig,
  namespaceApi,
  skillBundleApi,
  workbenchApi,
} from './client'

beforeEach(() => {
  setMockWindow()
})

afterEach(() => {
  vi.unstubAllGlobals()

  if (originalDocument) {
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: originalDocument,
    })
  } else {
    Reflect.deleteProperty(globalThis, 'document')
  }

  if (originalWindow) {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      writable: true,
      value: originalWindow,
    })
    return
  }

  Reflect.deleteProperty(globalThis, 'window')
})

describe('WEB_API_PREFIX', () => {
  it('uses the /api/web prefix for web-facing endpoints', () => {
    expect(WEB_API_PREFIX).toBe('/api/web')
  })
})

describe('buildApiUrl', () => {
  it('returns the path as-is when no runtime base URL is configured', () => {
    expect(buildApiUrl('/api/v1/auth/me')).toBe('/api/v1/auth/me')
  })

  it('prepends the runtime base URL when one is set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/api/v1/auth/me')
  })

  it('handles a trailing slash on the base URL', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/api/v1/auth/me')
  })

  it('preserves base URL path prefixes', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/skill_hub' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('https://api.example.com/skill_hub/api/v1/auth/me')
  })

  it('supports relative base URL path prefixes', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: '/skill_hub' }
    const url = buildApiUrl('/api/v1/auth/me')
    expect(url).toBe('/skill_hub/api/v1/auth/me')
  })
})

describe('fetchText', () => {
  it('applies base URL path prefixes for fetch requests', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com/skill_hub' }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      text: async () => 'ok',
    })
    vi.stubGlobal('fetch', fetchMock)

    await fetchText('/api/v1/auth/me')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/skill_hub/api/v1/auth/me',
      expect.objectContaining({
        headers: expect.any(Headers),
      }),
    )
  })
})

describe('namespaceApi.delete', () => {
  it('sends a DELETE request to the normalized namespace endpoint', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: {
        cookie: 'XSRF-TOKEN=test-token',
      },
    })

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: null,
        timestamp: '2026-05-07T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await namespaceApi.delete('@team-delete')

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/namespaces/team-delete',
      expect.objectContaining({
        method: 'DELETE',
        headers: expect.any(Headers),
      }),
    )
  })
})

describe('skillBundleApi', () => {
  it('queries expert package search using web endpoints and filter params', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: {
          items: [],
          total: 0,
          page: 0,
          size: 12,
        },
        timestamp: '2026-07-31T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await skillBundleApi.search({
      q: 'data',
      namespace: 'global',
      label: 'AI',
      sort: 'newest',
      page: 0,
      size: 12,
    })

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/skill-bundles?q=data&namespace=global&label=AI&sort=newest&page=0&size=12',
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
  })

  it('builds expert package download URLs and sends create/update requests', async () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: {
        cookie: 'XSRF-TOKEN=test-token',
      },
    })
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: {
          id: 1,
          namespace: 'global',
          slug: 'data-expert',
          name: 'Data Expert',
          summary: '',
          visibility: 'PUBLIC',
          status: 'PUBLISHED',
          skillCount: 0,
          labels: [],
          downloadCount: 0,
          updatedAt: '2026-07-31T00:00:00Z',
          items: [],
        },
        timestamp: '2026-07-31T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    expect(skillBundleApi.getDownloadUrl('@global', 'data expert')).toBe(
      'https://api.example.com/api/web/skill-bundles/global/data%20expert/download',
    )

    const request = {
      namespace: 'global',
      slug: 'data-expert',
      name: 'Data Expert',
      summary: 'bundle',
      items: [],
    }

    await skillBundleApi.create(request)
    await skillBundleApi.update('@global', 'data-expert', request)

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/skill-bundles',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(request),
        headers: expect.any(Headers),
      }),
    )
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/skill-bundles/global/data-expert',
      expect.objectContaining({
        method: 'PUT',
        body: JSON.stringify(request),
        headers: expect.any(Headers),
      }),
    )
  })
})

describe('workbenchApi MCP endpoints', () => {
  beforeEach(() => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = { apiBaseUrl: 'https://api.example.com' }
    Object.defineProperty(globalThis, 'document', {
      configurable: true,
      writable: true,
      value: {
        cookie: 'XSRF-TOKEN=test-token',
      },
    })
  })

  it('loads workbench runtime model configuration state', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: {
          modelExecutorEnabled: false,
          modelConfigured: false,
          modelProvider: null,
          modelName: null,
          modelBaseUrlConfigured: false,
          displayStatus: 'MODEL_NOT_CONFIGURED',
          message: '模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。',
        },
        timestamp: '2026-08-04T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await expect(workbenchApi.getRuntimeConfig()).resolves.toEqual({
      modelExecutorEnabled: false,
      modelConfigured: false,
      modelProvider: null,
      modelName: null,
      modelBaseUrlConfigured: false,
      displayStatus: 'MODEL_NOT_CONFIGURED',
      message: '模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。',
    })
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/runtime-config',
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
  })

  it('loads catalog metadata without relying on runtime endpoint fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: {
          items: [
            {
              id: 'ctx-tools',
              name: 'Context Tools',
              description: 'docs',
              enabled: true,
              tools: 3,
              resources: 1,
              prompts: 0,
              tags: ['知识库'],
              catalogSource: 'CONTEXT_FORGE',
              runtimeCandidate: true,
              streamableHttpUrl: 'https://internal.example.com/mcp',
              sseUrl: 'https://internal.example.com/sse',
            },
          ],
          total: 1,
          page: 0,
          size: 20,
        },
        timestamp: '2026-07-31T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    const catalog = await workbenchApi.listMcpCatalog(7, { search: 'ctx', page: 0, size: 20 })

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/mcp-catalog?search=ctx&page=0&size=20',
      expect.objectContaining({ headers: expect.any(Headers) }),
    )
    expect(catalog.items[0]).not.toHaveProperty('streamableHttpUrl')
    expect(catalog.items[0]).not.toHaveProperty('sseUrl')
  })

  it('posts selected MCP bindings', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: [],
        timestamp: '2026-07-31T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await workbenchApi.saveMcpBindings(7, { serverIds: ['ctx-tools'] })

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/mcp-bindings',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ serverIds: ['ctx-tools'] }),
        headers: expect.any(Headers),
      }),
    )
  })

  it('posts approval decisions to approve and reject endpoints', async () => {
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        msg: 'ok',
        data: null,
        timestamp: '2026-07-31T00:00:00Z',
        requestId: 'req-test',
      }),
    })
    vi.stubGlobal('fetch', fetchMock)

    await workbenchApi.approveApproval(7, 21)
    await workbenchApi.rejectApproval(7, 21)

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/approvals/21/approve',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/approvals/21/reject',
      expect.objectContaining({ method: 'POST', headers: expect.any(Headers) }),
    )
  })

  it('normalizes workbench file size fields from API responses', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          msg: 'ok',
          data: [
            { path: 'SKILL.md', size: 128, contentType: 'text/markdown' },
            { path: 'README.md', sizeBytes: '256', contentType: 'text/markdown' },
          ],
          timestamp: '2026-07-31T00:00:00Z',
          requestId: 'req-test',
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          msg: 'ok',
          data: { path: 'SKILL.md', size: 128, contentType: 'text/markdown', content: '# Skill\n' },
          timestamp: '2026-07-31T00:00:00Z',
          requestId: 'req-test',
        }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(workbenchApi.listFiles(7)).resolves.toEqual([
      { path: 'SKILL.md', sizeBytes: 128, contentType: 'text/markdown' },
      { path: 'README.md', sizeBytes: 256, contentType: 'text/markdown' },
    ])
    await expect(workbenchApi.readFile(7, 'SKILL.md')).resolves.toEqual({
      path: 'SKILL.md',
      sizeBytes: 128,
      contentType: 'text/markdown',
      content: '# Skill\n',
    })
  })

  it('previews and publishes a workbench package through session endpoints', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          msg: 'ok',
          data: {
            packageFingerprint: 'sha256:abc',
            readyToPublish: true,
            includedFiles: [{ path: 'SKILL.md', size: '128', sha256: 'file-sha' }],
            excludedFiles: [{ path: 'AGENTS.md', reason: 'WORKBENCH_RUNTIME_ARTIFACT' }],
            validation: { status: 'PASS', messages: ['ok'] },
          },
          timestamp: '2026-07-31T00:00:00Z',
          requestId: 'req-test',
        }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          code: 0,
          msg: 'ok',
          data: {
            skillId: 10,
            skillVersionId: 20,
            namespace: 'global',
            slug: 'hello',
            version: '1.0.0',
            status: 'UPLOADED',
          },
          timestamp: '2026-07-31T00:00:00Z',
          requestId: 'req-test',
        }),
      })
    vi.stubGlobal('fetch', fetchMock)

    await expect(workbenchApi.packagePreview(7, { visibility: 'PUBLIC' })).resolves.toEqual({
      packageFingerprint: 'sha256:abc',
      readyToPublish: true,
      includedFiles: [{ path: 'SKILL.md', sizeBytes: 128, sha256: 'file-sha' }],
      excludedFiles: [{ path: 'AGENTS.md', reason: 'WORKBENCH_RUNTIME_ARTIFACT' }],
      validation: { status: 'PASS', messages: ['ok'] },
    })
    await expect(workbenchApi.publishPackage(7, {
      confirmPackageFingerprint: 'sha256:abc',
      visibility: 'PRIVATE',
    })).resolves.toEqual({
      skillId: 10,
      skillVersionId: 20,
      namespace: 'global',
      slug: 'hello',
      version: '1.0.0',
      status: 'UPLOADED',
    })

    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/package-preview',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
        body: JSON.stringify({ visibility: 'PUBLIC' }),
      }),
    )
    expect(fetchMock).toHaveBeenCalledWith(
      'https://api.example.com/api/web/workbench/sessions/7/publish',
      expect.objectContaining({
        method: 'POST',
        headers: expect.any(Headers),
        body: JSON.stringify({ confirmPackageFingerprint: 'sha256:abc', visibility: 'PRIVATE' }),
      }),
    )
  })
})

describe('getDirectAuthRuntimeConfig', () => {
  it('returns disabled when no runtime config is present', () => {
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
    expect(config.provider).toBeUndefined()
  })

  it('returns enabled with provider when both flag and provider are set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'true',
      authDirectProvider: 'ldap',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(true)
    expect(config.provider).toBe('ldap')
  })

  it('returns disabled when the flag is true but the provider is missing', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'true',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
  })

  it('returns disabled when the flag is false', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authDirectEnabled: 'false',
      authDirectProvider: 'ldap',
    }
    const config = getDirectAuthRuntimeConfig()
    expect(config.enabled).toBe(false)
  })

  it('treats various truthy flag values correctly', () => {
    for (const flag of ['1', 'yes', 'on', 'TRUE', ' True ']) {
      window.__SKILLHUB_RUNTIME_CONFIG__ = {
        authDirectEnabled: flag,
        authDirectProvider: 'ldap',
      }
      expect(getDirectAuthRuntimeConfig().enabled).toBe(true)
    }
  })
})

describe('getSessionBootstrapRuntimeConfig', () => {
  it('returns disabled when no runtime config is present', () => {
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(false)
    expect(config.auto).toBe(false)
    expect(config.provider).toBeUndefined()
  })

  it('returns fully enabled config when all flags and provider are set', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authSessionBootstrapEnabled: '1',
      authSessionBootstrapProvider: 'sso',
      authSessionBootstrapAuto: 'true',
    }
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(true)
    expect(config.provider).toBe('sso')
    expect(config.auto).toBe(true)
  })

  it('returns disabled when the provider is blank', () => {
    window.__SKILLHUB_RUNTIME_CONFIG__ = {
      authSessionBootstrapEnabled: 'true',
      authSessionBootstrapProvider: '  ',
    }
    const config = getSessionBootstrapRuntimeConfig()
    expect(config.enabled).toBe(false)
  })
})

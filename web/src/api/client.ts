import createClient from 'openapi-fetch'
import type { paths } from './generated/schema'
import type {
  ChangePasswordRequest,
  PasswordResetConfirmRequest,
  PasswordResetRequest,
  ApiToken,
  CreateTokenRequest,
  CreateTokenResponse,
  MergeConfirmRequest,
  LocalLoginRequest,
  LocalRegisterRequest,
  MergeInitiateRequest,
  MergeInitiateResponse,
  MergeVerifyRequest,
  ReviewSkillDetail,
  ReviewTask,
  PromotionSortBy,
  PromotionSortDirection,
  PromotionStatus,
  PromotionTask,
  AuditLogItem,
  SkillSummary,
  SkillVersion,
  SkillReport,
  GovernanceSummary,
  GovernanceInboxItem,
  GovernanceActivityItem,
  GovernanceNotification,
  PagedResponse,
  ReportDisposition,
  AuthMethod,
  OAuthProvider,
  User,
  ManagedNamespace,
  Namespace,
  CreateNamespaceRequest,
  NamespaceMember,
  NamespaceCandidateUser,
  NotificationItem,
  NotificationPreferenceItem,
  NotificationUnreadCount,
  SkillDeleteResult,
  AdminLabelInput,
  LabelDefinition,
  LabelItem,
  BatchMemberResponse,
  SearchParams,
  SkillBundleDetail,
  SkillBundleDraftRequest,
  SkillBundleSummary,
  CreateWorkbenchSessionRequest,
  ImportWorkbenchSourceResult,
  WorkbenchDiffResult,
  WorkbenchFile,
  WorkbenchFileContent,
  WorkbenchMcpAssociatedItem,
  WorkbenchMcpBinding,
  WorkbenchMcpCatalogItem,
  WorkbenchMcpCatalogResponse,
  WorkbenchPackagePreview,
  WorkbenchPackagePreviewRequest,
  WorkbenchPublishResult,
  WorkbenchRuntimeConfig,
  WorkbenchRuntimeRunResponse,
  WorkbenchRuntimeStreamEvent,
  PublishWorkbenchPackageRequest,
  SaveWorkbenchMcpBindingsRequest,
  WorkbenchSession,
  WorkbenchSessionEvent,
  WorkbenchToolApproval,
  WriteWorkbenchFileRequest,
} from './types'
import { ApiError } from '@/shared/lib/api-error'
import i18n from '@/i18n/config'

/**
 * Front-end API foundation for generated OpenAPI calls and hand-written convenience wrappers.
 *
 * This module centralizes runtime-config lookup, CSRF handling, localized request headers, envelope
 * unwrapping, and exported API groups used throughout feature hooks.
 */
export { ApiError }

export const WEB_API_PREFIX = '/api/web'

type RuntimeConfig = {
  apiBaseUrl?: string
  appBaseUrl?: string
  authDirectEnabled?: string
  authDirectProvider?: string
  authSessionBootstrapEnabled?: string
  authSessionBootstrapProvider?: string
  authSessionBootstrapAuto?: string
}

declare global {
  interface Window {
    __SKILLHUB_RUNTIME_CONFIG__?: RuntimeConfig
  }
}

function getRuntimeConfig(): RuntimeConfig {
  if (typeof window === 'undefined') {
    return {}
  }
  return window.__SKILLHUB_RUNTIME_CONFIG__ ?? {}
}

function getApiBaseUrl(): string {
  return getRuntimeConfig().apiBaseUrl ?? ''
}

function parseBooleanFlag(value: string | undefined): boolean {
  if (!value) {
    return false
  }
  return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase())
}

const client = createClient<paths>({ baseUrl: getApiBaseUrl() })

function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

function withRequestHeaders(headers?: HeadersInit): Headers {
  const merged = new Headers(headers)
  const language = i18n.resolvedLanguage?.trim()
  if (language) {
    merged.set('Accept-Language', language)
  }
  return merged
}

function withCsrf(headers?: HeadersInit): HeadersInit {
  const merged = withRequestHeaders(headers)
  const csrfToken = getCsrfToken()
  if (!csrfToken) {
    return merged
  }

  merged.set('X-XSRF-TOKEN', csrfToken)
  return merged
}

async function ensureCsrfHeaders(headers?: HeadersInit): Promise<HeadersInit> {
  if (!getCsrfToken()) {
    await client.GET('/api/v1/auth/providers', {
      headers: withRequestHeaders(),
    } as never)
  }
  return withCsrf(headers)
}

function isApiEnvelope<T>(value: unknown): value is ApiEnvelope<T> {
  return typeof value === 'object' && value !== null && 'code' in value && 'msg' in value && 'data' in value
}

export function getCsrfHeaders(headers?: HeadersInit): HeadersInit {
  return withCsrf(headers)
}

export type SessionBootstrapRuntimeConfig = {
  enabled: boolean
  provider?: string
  auto: boolean
}

export type DirectAuthRuntimeConfig = {
  enabled: boolean
  provider?: string
}

export function getDirectAuthRuntimeConfig(): DirectAuthRuntimeConfig {
  const config = getRuntimeConfig()
  const provider = config.authDirectProvider?.trim()
  return {
    enabled: parseBooleanFlag(config.authDirectEnabled) && !!provider,
    provider: provider || undefined,
  }
}

export function getSessionBootstrapRuntimeConfig(): SessionBootstrapRuntimeConfig {
  const config = getRuntimeConfig()
  const provider = config.authSessionBootstrapProvider?.trim()
  return {
    enabled: parseBooleanFlag(config.authSessionBootstrapEnabled) && !!provider,
    provider: provider || undefined,
    auto: parseBooleanFlag(config.authSessionBootstrapAuto),
  }
}

type ApiEnvelope<T> = {
  code: number
  msg: string
  data: T
  timestamp: string
  requestId: string
}

type RequestWithTimeout = RequestInit & {
  timeoutMs?: number
}

function createRequestSignal(init?: RequestWithTimeout): { signal?: AbortSignal, cleanup: () => void } {
  if (!init?.timeoutMs && !init?.signal) {
    return { signal: init?.signal ?? undefined, cleanup: () => {} }
  }

  const controller = new AbortController()
  const timeoutId = init?.timeoutMs ? window.setTimeout(() => controller.abort('timeout'), init.timeoutMs) : undefined
  const abortListener = () => controller.abort()

  if (init?.signal) {
    if (init.signal.aborted) {
      controller.abort()
    } else {
      init.signal.addEventListener('abort', abortListener, { once: true })
    }
  }

  return {
    signal: controller.signal,
    cleanup: () => {
      if (timeoutId !== undefined) {
        window.clearTimeout(timeoutId)
      }
      init?.signal?.removeEventListener('abort', abortListener)
    },
  }
}

export async function fetchJson<T>(input: RequestInfo | URL, init?: RequestWithTimeout): Promise<T> {
  const { signal, cleanup } = createRequestSignal(init)
  let response: Response
  try {
    response = await fetch(withBaseUrl(input), {
      ...init,
      signal,
      headers: withRequestHeaders(init?.headers),
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('error.request.timeout', 408)
    }
    throw new ApiError('apiError.networkError', 0)
  } finally {
    cleanup()
  }

  let json: ApiEnvelope<T> | null = null

  try {
    json = (await response.json()) as ApiEnvelope<T>
  } catch {
    if (!response.ok) {
      throw new ApiError(`HTTP ${response.status}`, response.status)
    }
    throw new ApiError('Invalid JSON response', response.status)
  }

  if (!response.ok || json.code !== 0) {
    throw new ApiError(json.msg || `HTTP ${response.status}`, response.status, json.msg, json.msg)
  }

  return json.data
}

export async function fetchText(input: RequestInfo | URL, init?: RequestInit): Promise<string> {
  const response = await fetch(withBaseUrl(input), {
    ...init,
    headers: withRequestHeaders(init?.headers),
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  return response.text()
}

function withBaseUrl(input: RequestInfo | URL): RequestInfo | URL {
  const baseUrl = getApiBaseUrl()
  if (!baseUrl || typeof input !== 'string' || !input.startsWith('/')) {
    return input
  }
  return prependApiBaseUrl(baseUrl, input)
}

export function buildApiUrl(path: string): string {
  const baseUrl = getApiBaseUrl()
  if (!baseUrl) {
    return path
  }
  return prependApiBaseUrl(baseUrl, path)
}

function prependApiBaseUrl(baseUrl: string, path: string): string {
  const normalizedBaseUrl = trimTrailingSlash(baseUrl)
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBaseUrl}${normalizedPath}`
}

function trimTrailingSlash(value: string): string {
  if (value.length > 1 && value.endsWith('/')) {
    return value.slice(0, -1)
  }
  return value
}

export async function getCurrentUser(): Promise<User | null> {
  try {
    const user = await fetchJson<User>('/api/v1/auth/me')
    return {
      ...user,
      userId: user.userId ?? '',
      displayName: user.displayName ?? '',
      platformRoles: user.platformRoles ?? [],
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export const authApi = {
  getMe: getCurrentUser,

  async getProviders(returnTo?: string): Promise<OAuthProvider[]> {
    const params = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''
    const providers = await fetchJson<OAuthProvider[]>(`/api/v1/auth/providers${params}`)
    return providers
      .filter((provider) => provider.id && provider.name && provider.authorizationUrl)
      .map((provider) => ({
        ...provider,
        id: provider.id!,
        name: provider.name!,
        authorizationUrl: provider.authorizationUrl!,
      }))
  },

  async getMethods(returnTo?: string): Promise<AuthMethod[]> {
    const query = returnTo ? `?returnTo=${encodeURIComponent(returnTo)}` : ''
    const methods = await fetchJson<AuthMethod[]>(`/api/v1/auth/methods${query}`)
    return methods
      .filter((method) => method.id && method.methodType && method.provider && method.displayName && method.actionUrl)
      .map((method) => ({
        ...method,
        id: method.id,
        methodType: method.methodType,
        provider: method.provider,
        displayName: method.displayName,
        actionUrl: method.actionUrl,
      }))
  },

  async localLogin(request: LocalLoginRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/local/login', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async localRegister(request: LocalRegisterRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/local/register', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async changePassword(request: ChangePasswordRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/change-password', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async requestPasswordReset(request: PasswordResetRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/password-reset/request', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async confirmPasswordReset(request: PasswordResetConfirmRequest): Promise<void> {
    await fetchJson<void>('/api/v1/auth/local/password-reset/confirm', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async logout(): Promise<void> {
    const response = await fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: withCsrf(),
    })
    if (response.status !== 200 && response.status !== 204) {
      throw new Error(`HTTP ${response.status}`)
    }
  },

  async bootstrapSession(provider: string): Promise<User> {
    return fetchJson<User>('/api/v1/auth/session/bootstrap', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ provider }),
    })
  },

  async directLogin(provider: string, request: LocalLoginRequest): Promise<User> {
    return fetchJson<User>('/api/v1/auth/direct/login', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        provider,
        username: request.username,
        password: request.password,
      }),
    })
  },
}

export const accountApi = {
  async initiateMerge(request: MergeInitiateRequest): Promise<MergeInitiateResponse> {
    return fetchJson<MergeInitiateResponse>('/api/v1/account/merge/initiate', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async verifyMerge(request: MergeVerifyRequest): Promise<void> {
    await fetchJson<void>('/api/v1/account/merge/verify', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async confirmMerge(request: MergeConfirmRequest): Promise<void> {
    await fetchJson<void>('/api/v1/account/merge/confirm', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },
}

export const skillLifecycleApi = {
  async archiveSkill(namespace: string, slug: string, reason?: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/archive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(reason?.trim() ? { reason: reason.trim() } : {}),
    })
  },

  async unarchiveSkill(namespace: string, slug: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/unarchive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async deleteSkill(namespace: string, slug: string, ownerId?: string): Promise<SkillDeleteResult> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    const params = ownerId ? `?ownerId=${encodeURIComponent(ownerId)}` : ''
    return fetchJson<SkillDeleteResult>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}${params}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async deleteVersion(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async withdrawReview(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/withdraw-review`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async rereleaseVersion(namespace: string, slug: string, version: string, targetVersion: string, confirmWarnings = false): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions/${encodeURIComponent(version)}/rerelease`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ targetVersion, confirmWarnings }),
    })
  },

  /**
   * Submit an UPLOADED version for review.
   * Transitions version status from UPLOADED to PENDING_REVIEW.
   */
  async submitForReview(namespace: string, slug: string, version: string, targetVisibility: 'PUBLIC' | 'NAMESPACE_ONLY'): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/submit-review`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ version, targetVisibility }),
    })
  },

  /**
   * Confirm publish for a PRIVATE skill version.
   * Transitions version status from UPLOADED to PUBLISHED without review.
   */
  async confirmPublish(namespace: string, slug: string, version: string): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/confirm-publish`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ version }),
    })
  },

  async listVersions(namespace: string, slug: string, params?: { page?: number; size?: number }): Promise<{ items: SkillVersion[]; total: number; page: number; size: number }> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 50))
    return fetchJson<{ items: SkillVersion[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/versions?${searchParams.toString()}`,
    )
  },
}

async function fetchSse(
  input: RequestInfo | URL,
  init: RequestInit | undefined,
  onEvent: (event: WorkbenchRuntimeStreamEvent) => void,
): Promise<void> {
  const response = await fetch(withBaseUrl(input), {
    ...init,
    headers: withRequestHeaders(init?.headers),
  })
  if (!response.ok) {
    throw new ApiError(`HTTP ${response.status}`, response.status)
  }
  if (!response.body) {
    throw new ApiError('Streaming response is not available', response.status)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\n\n|\r\n\r\n/)
    buffer = blocks.pop() ?? ''
    for (const block of blocks) {
      const parsed = parseSseBlock(block)
      if (parsed) {
        onEvent(parsed)
      }
    }
  }
  buffer += decoder.decode()
  const parsed = parseSseBlock(buffer)
  if (parsed) {
    onEvent(parsed)
  }
}

function parseSseBlock(block: string): WorkbenchRuntimeStreamEvent | null {
  const lines = block.split(/\r?\n/)
  let event = 'message'
  const data: string[] = []
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice('event:'.length).trim()
    } else if (line.startsWith('data:')) {
      data.push(line.slice('data:'.length).trimStart())
    }
  }
  if (data.length === 0) {
    return null
  }
  try {
    const parsed = JSON.parse(data.join('\n'))
    return {
      event,
      data: typeof parsed === 'object' && parsed !== null ? parsed as Record<string, unknown> : { value: parsed },
    }
  } catch {
    return { event, data: { raw: data.join('\n') } }
  }
}

function normalizeNamespaceSlug(namespace: string): string {
  return namespace.startsWith('@') ? namespace.slice(1) : namespace
}

const WORKBENCH_API_PREFIX = `${WEB_API_PREFIX}/workbench`

function sessionPath(sessionId: number | string, suffix = '') {
  return `${WORKBENCH_API_PREFIX}/sessions/${encodeURIComponent(String(sessionId))}${suffix}`
}

function associatedItems(value: unknown): WorkbenchMcpAssociatedItem[] {
  return Array.isArray(value)
    ? value
        .filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
        .map((item) => ({
          id: String(item.id ?? item.name ?? ''),
          name: String(item.name ?? item.id ?? ''),
          description: typeof item.description === 'string' ? item.description : null,
        }))
        .filter((item) => item.id || item.name)
    : []
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string')
    : []
}

function objectValue(value: unknown): Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {}
}

function arrayValue(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function sanitizeWorkbenchMcpCatalogItem(item: Record<string, unknown>): WorkbenchMcpCatalogItem {
  return {
    id: String(item.id ?? ''),
    name: String(item.name ?? item.id ?? ''),
    description: typeof item.description === 'string' ? item.description : null,
    enabled: item.enabled === true,
    tools: associatedItems(item.tools),
    resources: associatedItems(item.resources),
    prompts: associatedItems(item.prompts),
    tags: stringList(item.tags),
    catalogSource: String(item.catalogSource ?? ''),
    runtimeCandidate: item.runtimeCandidate === true,
  }
}

function sanitizeWorkbenchMcpBinding(binding: Record<string, unknown>): WorkbenchMcpBinding {
  return {
    id: Number(binding.id ?? 0),
    serverId: String(binding.serverId ?? ''),
    catalogSource: String(binding.catalogSource ?? ''),
    enabledToolsJson: arrayValue(binding.enabledToolsJson),
    disabledToolsJson: arrayValue(binding.disabledToolsJson),
    toolPolicyJson: objectValue(binding.toolPolicyJson),
    policyVersion: String(binding.policyVersion ?? ''),
    status: String(binding.status ?? ''),
  }
}

function sanitizeWorkbenchToolApproval(approval: unknown): WorkbenchToolApproval {
  const raw = objectValue(approval)
  return {
    id: Number(raw.id ?? 0),
    sessionId: Number(raw.sessionId ?? 0),
    eventId: Number(raw.eventId ?? 0),
    toolName: String(raw.toolName ?? ''),
    mcpServerId: typeof raw.mcpServerId === 'string' ? raw.mcpServerId : null,
    riskLevel: String(raw.riskLevel ?? ''),
    argumentsRedactedJson: objectValue(raw.argumentsRedactedJson),
    status: String(raw.status ?? ''),
    decisionBy: typeof raw.decisionBy === 'string' ? raw.decisionBy : null,
    decisionAt: typeof raw.decisionAt === 'string' ? raw.decisionAt : null,
    createdAt: String(raw.createdAt ?? ''),
  }
}

function numericValue(...values: unknown[]): number {
  for (const value of values) {
    const parsed = typeof value === 'number'
      ? value
      : typeof value === 'string'
        ? Number(value)
        : Number.NaN
    if (Number.isFinite(parsed) && parsed >= 0) {
      return parsed
    }
  }
  return 0
}

function sanitizeWorkbenchFile(file: unknown): WorkbenchFile {
  const raw = objectValue(file)
  return {
    path: String(raw.path ?? ''),
    sizeBytes: numericValue(raw.sizeBytes, raw.size),
    contentType: typeof raw.contentType === 'string' ? raw.contentType : null,
  }
}

function sanitizeWorkbenchFileContent(file: unknown): WorkbenchFileContent {
  const raw = objectValue(file)
  return {
    ...sanitizeWorkbenchFile(raw),
    content: typeof raw.content === 'string' ? raw.content : '',
  }
}

function sanitizeWorkbenchPackagePreview(value: unknown): WorkbenchPackagePreview {
  const raw = objectValue(value)
  const validation = objectValue(raw.validation)
  const includedFiles = Array.isArray(raw.includedFiles) ? raw.includedFiles : []
  const excludedFiles = Array.isArray(raw.excludedFiles) ? raw.excludedFiles : []
  return {
    packageFingerprint: String(raw.packageFingerprint ?? ''),
    readyToPublish: raw.readyToPublish === true,
    includedFiles: includedFiles
      .map((file) => objectValue(file))
      .map((file) => ({
        path: String(file.path ?? ''),
        sizeBytes: numericValue(file.sizeBytes, file.size),
        sha256: typeof file.sha256 === 'string' ? file.sha256 : undefined,
      }))
      .filter((file) => file.path),
    excludedFiles: excludedFiles
      .map((file) => objectValue(file))
      .map((file) => ({
        path: String(file.path ?? ''),
        reason: String(file.reason ?? ''),
      }))
      .filter((file) => file.path),
    validation: {
      status: String(validation.status ?? ''),
      messages: stringList(validation.messages),
    },
  }
}

function sanitizeWorkbenchRuntimeConfig(value: unknown): WorkbenchRuntimeConfig {
  const raw = objectValue(value)
  return {
    modelExecutorEnabled: raw.modelExecutorEnabled === true,
    modelConfigured: raw.modelConfigured === true,
    modelProvider: typeof raw.modelProvider === 'string' ? raw.modelProvider : null,
    modelName: typeof raw.modelName === 'string' ? raw.modelName : null,
    modelBaseUrlConfigured: raw.modelBaseUrlConfigured === true,
    displayStatus: String(raw.displayStatus ?? ''),
    message: typeof raw.message === 'string'
      ? raw.message
      : '模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。',
  }
}

export const workbenchApi = {
  async getRuntimeConfig(): Promise<WorkbenchRuntimeConfig> {
    return sanitizeWorkbenchRuntimeConfig(await fetchJson<unknown>(`${WORKBENCH_API_PREFIX}/runtime-config`))
  },

  async listSessions(params?: { limit?: number }): Promise<WorkbenchSession[]> {
    const searchParams = new URLSearchParams()
    searchParams.set('limit', String(params?.limit ?? 20))
    return fetchJson<WorkbenchSession[]>(`${WORKBENCH_API_PREFIX}/sessions?${searchParams.toString()}`)
  },

  async createSession(request: CreateWorkbenchSessionRequest): Promise<WorkbenchSession> {
    return fetchJson<WorkbenchSession>(`${WORKBENCH_API_PREFIX}/sessions`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async getSession(sessionId: number | string): Promise<WorkbenchSession> {
    return fetchJson<WorkbenchSession>(sessionPath(sessionId))
  },

  async importSource(sessionId: number | string): Promise<ImportWorkbenchSourceResult> {
    const result = await fetchJson<Partial<ImportWorkbenchSourceResult> & { files?: string[] }>(sessionPath(sessionId, '/import-source'), {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
    return {
      importedFiles: Array.isArray(result.importedFiles)
        ? result.importedFiles
        : Array.isArray(result.files)
          ? result.files
          : [],
    }
  },

  async listEvents(sessionId: number | string, params?: { afterEventId?: number; limit?: number }): Promise<WorkbenchSessionEvent[]> {
    const searchParams = new URLSearchParams()
    if (params?.afterEventId !== undefined) {
      searchParams.set('afterEventId', String(params.afterEventId))
    }
    if (params?.limit !== undefined) {
      searchParams.set('limit', String(params.limit))
    }
    const query = searchParams.size > 0 ? `?${searchParams.toString()}` : ''
    return fetchJson<WorkbenchSessionEvent[]>(sessionPath(sessionId, `/events${query}`))
  },

  async listFiles(sessionId: number | string): Promise<WorkbenchFile[]> {
    const response = await fetchJson<unknown[]>(sessionPath(sessionId, '/files'))
    return response
      .map(sanitizeWorkbenchFile)
      .filter((file) => file.path)
  },

  async listMcpCatalog(
    sessionId: number | string,
    params?: { search?: string; page?: number; size?: number },
  ): Promise<WorkbenchMcpCatalogResponse> {
    const searchParams = new URLSearchParams()
    if (params?.search) {
      searchParams.set('search', params.search)
    }
    if (params?.page !== undefined) {
      searchParams.set('page', String(params.page))
    }
    if (params?.size !== undefined) {
      searchParams.set('size', String(params.size))
    }
    const query = searchParams.size > 0 ? `?${searchParams.toString()}` : ''
    const response = objectValue(await fetchJson<unknown>(sessionPath(sessionId, `/mcp-catalog${query}`)))
    return {
      items: Array.isArray(response.items)
        ? response.items
            .filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
            .map(sanitizeWorkbenchMcpCatalogItem)
        : [],
      total: Number(response.total ?? 0),
      page: Number(response.page ?? params?.page ?? 0),
      size: Number(response.size ?? params?.size ?? 20),
    }
  },

  async listMcpBindings(sessionId: number | string): Promise<WorkbenchMcpBinding[]> {
    const response = await fetchJson<unknown[]>(sessionPath(sessionId, '/mcp-bindings'))
    return response
      .filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
      .map(sanitizeWorkbenchMcpBinding)
      .filter((binding) => binding.id > 0 && binding.serverId)
  },

  async saveMcpBindings(
    sessionId: number | string,
    request: SaveWorkbenchMcpBindingsRequest,
  ): Promise<WorkbenchMcpBinding[]> {
    const response = await fetchJson<unknown[]>(sessionPath(sessionId, '/mcp-bindings'), {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
    return response
      .filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
      .map(sanitizeWorkbenchMcpBinding)
      .filter((binding) => binding.id > 0 && binding.serverId)
  },

  async listApprovals(sessionId: number | string): Promise<WorkbenchToolApproval[]> {
    const response = await fetchJson<unknown[]>(sessionPath(sessionId, '/approvals'))
    return response
      .filter((item): item is Record<string, unknown> => typeof item === 'object' && item !== null)
      .map(sanitizeWorkbenchToolApproval)
      .filter((approval) => approval.id > 0)
  },

  async readFile(sessionId: number | string, path: string): Promise<WorkbenchFileContent> {
    return sanitizeWorkbenchFileContent(await fetchJson<unknown>(sessionPath(sessionId, `/file?path=${encodeURIComponent(path)}`)))
  },

  async writeFile(sessionId: number | string, path: string, request: WriteWorkbenchFileRequest): Promise<WorkbenchFileContent> {
    return fetchJson<WorkbenchFileContent>(sessionPath(sessionId, `/file?path=${encodeURIComponent(path)}`), {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async deleteFile(sessionId: number | string, path: string): Promise<void> {
    await fetchJson<void>(sessionPath(sessionId, `/file?path=${encodeURIComponent(path)}`), {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async getDiff(sessionId: number | string): Promise<WorkbenchDiffResult> {
    const result = await fetchJson<Partial<WorkbenchDiffResult>>(sessionPath(sessionId, '/diff'))
    return {
      files: Array.isArray(result.files) ? result.files : [],
    }
  },

  async readyForReview(sessionId: number | string): Promise<WorkbenchSession> {
    return fetchJson<WorkbenchSession>(sessionPath(sessionId, '/ready-for-review'), {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async packagePreview(sessionId: number | string, request?: WorkbenchPackagePreviewRequest): Promise<WorkbenchPackagePreview> {
    return sanitizeWorkbenchPackagePreview(await fetchJson<unknown>(sessionPath(sessionId, '/package-preview'), {
      method: 'POST',
      headers: await ensureCsrfHeaders(request?.visibility ? {
        'Content-Type': 'application/json',
      } : undefined),
      body: request?.visibility ? JSON.stringify(request) : undefined,
    }))
  },

  async publishPackage(sessionId: number | string, request: PublishWorkbenchPackageRequest): Promise<WorkbenchPublishResult> {
    return fetchJson<WorkbenchPublishResult>(sessionPath(sessionId, '/publish'), {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async sendMessage(sessionId: number | string, message: string): Promise<WorkbenchRuntimeRunResponse> {
    return fetchJson<WorkbenchRuntimeRunResponse>(sessionPath(sessionId, '/messages'), {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ message }),
    })
  },

  async sendMessageStream(
    sessionId: number | string,
    message: string,
    onEvent: (event: WorkbenchRuntimeStreamEvent) => void,
  ): Promise<void> {
    await fetchSse(sessionPath(sessionId, '/messages/stream'), {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
      }),
      body: JSON.stringify({ message }),
    }, onEvent)
  },

  async cancelRun(sessionId: number | string, runId: string): Promise<WorkbenchRuntimeRunResponse> {
    return fetchJson<WorkbenchRuntimeRunResponse>(
      sessionPath(sessionId, `/runs/${encodeURIComponent(runId)}/cancel`),
      {
        method: 'POST',
        headers: await ensureCsrfHeaders(),
      },
    )
  },

  async approveApproval(sessionId: number | string, approvalId: number | string): Promise<WorkbenchToolApproval> {
    const response = await fetchJson<unknown>(
      sessionPath(sessionId, `/approvals/${encodeURIComponent(String(approvalId))}/approve`),
      {
        method: 'POST',
        headers: await ensureCsrfHeaders(),
      },
    )
    return sanitizeWorkbenchToolApproval(response)
  },

  async rejectApproval(sessionId: number | string, approvalId: number | string): Promise<WorkbenchToolApproval> {
    const response = await fetchJson<unknown>(
      sessionPath(sessionId, `/approvals/${encodeURIComponent(String(approvalId))}/reject`),
      {
        method: 'POST',
        headers: await ensureCsrfHeaders(),
      },
    )
    return sanitizeWorkbenchToolApproval(response)
  },
}

export const labelApi = {
  async listVisible(): Promise<LabelItem[]> {
    return fetchJson<LabelItem[]>(`${WEB_API_PREFIX}/labels`)
  },

  async listSkillLabels(namespace: string, slug: string): Promise<LabelItem[]> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<LabelItem[]>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels`)
  },

  async attachSkillLabel(namespace: string, slug: string, labelSlug: string): Promise<LabelItem> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<LabelItem>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels/${encodeURIComponent(labelSlug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders(),
    })
  },

  async detachSkillLabel(namespace: string, slug: string, labelSlug: string): Promise<void> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/labels/${encodeURIComponent(labelSlug)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async listAdminDefinitions(): Promise<LabelDefinition[]> {
    return fetchJson<LabelDefinition[]>('/api/v1/admin/labels')
  },

  async createAdminDefinition(request: AdminLabelInput): Promise<LabelDefinition> {
    return fetchJson<LabelDefinition>('/api/v1/admin/labels', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        slug: request.slug.trim(),
        type: request.type,
        visibleInFilter: request.visibleInFilter,
        sortOrder: request.sortOrder,
        translations: request.translations.map((translation) => ({
          locale: translation.locale.trim(),
          displayName: translation.displayName.trim(),
        })),
      }),
    })
  },

  async updateAdminDefinition(slug: string, request: Omit<AdminLabelInput, 'slug'>): Promise<LabelDefinition> {
    return fetchJson<LabelDefinition>(`/api/v1/admin/labels/${encodeURIComponent(slug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        type: request.type,
        visibleInFilter: request.visibleInFilter,
        sortOrder: request.sortOrder,
        translations: request.translations.map((translation) => ({
          locale: translation.locale.trim(),
          displayName: translation.displayName.trim(),
        })),
      }),
    })
  },

  async deleteAdminDefinition(slug: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/labels/${encodeURIComponent(slug)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async updateAdminSortOrder(items: Array<{ slug: string; sortOrder: number }>): Promise<LabelDefinition[]> {
    return fetchJson<LabelDefinition[]>('/api/v1/admin/labels/sort-order', {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ items }),
    })
  },
}

export const skillBundleApi = {
  async search(params: SearchParams): Promise<PagedResponse<SkillBundleSummary>> {
    const searchParams = new URLSearchParams()
    if (params.q !== undefined) {
      searchParams.set('q', params.q)
    }
    if (params.namespace) {
      searchParams.set('namespace', normalizeNamespaceSlug(params.namespace))
    }
    if (params.label) {
      searchParams.set('label', params.label)
    }
    if (params.sort) {
      searchParams.set('sort', params.sort)
    }
    if (params.page !== undefined) {
      searchParams.set('page', String(params.page))
    }
    if (params.size !== undefined) {
      searchParams.set('size', String(params.size))
    }
    const query = searchParams.toString()
    return fetchJson<PagedResponse<SkillBundleSummary>>(`${WEB_API_PREFIX}/skill-bundles${query ? `?${query}` : ''}`)
  },

  async getDetail(namespace: string, slug: string): Promise<SkillBundleDetail> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<SkillBundleDetail>(`${WEB_API_PREFIX}/skill-bundles/${cleanNamespace}/${encodeURIComponent(slug)}`)
  },

  getDownloadUrl(namespace: string, slug: string): string {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return buildApiUrl(`${WEB_API_PREFIX}/skill-bundles/${encodeURIComponent(cleanNamespace)}/${encodeURIComponent(slug)}/download`)
  },

  async create(request: SkillBundleDraftRequest): Promise<SkillBundleDetail> {
    return fetchJson<SkillBundleDetail>(`${WEB_API_PREFIX}/skill-bundles`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async update(namespace: string, slug: string, request: SkillBundleDraftRequest): Promise<SkillBundleDetail> {
    const cleanNamespace = normalizeNamespaceSlug(namespace)
    return fetchJson<SkillBundleDetail>(`${WEB_API_PREFIX}/skill-bundles/${cleanNamespace}/${encodeURIComponent(slug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },
}

export const namespaceApi = {
  async create(request: CreateNamespaceRequest): Promise<Namespace> {
    const namespace = await fetchJson<Namespace>('/api/v1/namespaces', {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        slug: normalizeNamespaceSlug(request.slug),
        displayName: request.displayName.trim(),
        description: request.description?.trim() || undefined,
      }),
    })
    return namespace
  },

  async listMine(): Promise<ManagedNamespace[]> {
    return fetchJson<ManagedNamespace[]>(`${WEB_API_PREFIX}/me/namespaces`)
  },

  async getDetail(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}`)
  },

  async freeze(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/freeze`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async unfreeze(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/unfreeze`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async archive(slug: string, reason?: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/archive`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(reason?.trim() ? { reason: reason.trim() } : {}),
    })
  },

  async restore(slug: string): Promise<Namespace> {
    return fetchJson<Namespace>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/restore`, {
      method: 'POST',
      headers: await ensureCsrfHeaders(),
    })
  },

  async delete(slug: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async listMembers(slug: string, params?: { page?: number; size?: number }): Promise<PagedResponse<NamespaceMember>> {
    const queryPage = params?.page ?? 0
    const querySize = params?.size ?? 20
    return fetchJson<PagedResponse<NamespaceMember>>(
      `${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members?page=${queryPage}&size=${querySize}`,
    )
  },

  async searchMemberCandidates(slug: string, search: string, size = 10): Promise<NamespaceCandidateUser[]> {
    const query = new URLSearchParams({
      search: search.trim(),
      size: String(size),
    })
    return fetchJson<NamespaceCandidateUser[]>(
      `${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/member-candidates?${query.toString()}`,
    )
  },

  async addMember(slug: string, request: { userId: string; role: string }): Promise<NamespaceMember> {
    return fetchJson<NamespaceMember>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({
        userId: request.userId.trim(),
        role: request.role,
      }),
    })
  },

  async batchAddMembers(slug: string, members: Array<{ userId: string; role: string }>): Promise<BatchMemberResponse> {
    return fetchJson<BatchMemberResponse>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/batch`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ members }),
    })
  },

  async updateMemberRole(slug: string, userId: string, role: string): Promise<NamespaceMember> {
    return fetchJson<NamespaceMember>(
      `${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/${encodeURIComponent(userId)}/role`,
      {
        method: 'PUT',
        headers: await ensureCsrfHeaders({
          'Content-Type': 'application/json',
        }),
        body: JSON.stringify({ role }),
      },
    )
  },

  async removeMember(slug: string, userId: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/members/${encodeURIComponent(userId)}`, {
      method: 'DELETE',
      headers: await ensureCsrfHeaders(),
    })
  },

  async update(slug: string, request: { displayName?: string; description?: string }): Promise<Namespace> {
    const body: Record<string, string> = {}
    if (request.displayName !== undefined) {
      body.displayName = request.displayName.trim()
    }
    if (request.description !== undefined) {
      body.description = request.description === '' ? '' : request.description.trim()
    }
    return fetchJson<Namespace>(`/api/v1/namespaces/${normalizeNamespaceSlug(slug)}`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(body),
    })
  },

  async transferOwnership(slug: string, newOwnerUserId: string): Promise<{ message: string }> {
    return fetchJson<{ message: string }>(`${WEB_API_PREFIX}/namespaces/${normalizeNamespaceSlug(slug)}/transfer-ownership`, {
      method: 'POST',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ newOwnerId: newOwnerUserId.trim() }),
    })
  },
}

export const tokenApi = {
  async getTokens(params?: { page?: number, size?: number }): Promise<{ items: ApiToken[], total: number, page: number, size: number }> {
    const queryPage = params?.page ?? 0
    const querySize = params?.size ?? 10
    const page = await fetchJson<{ items: ApiToken[], total: number, page: number, size: number }>(
      `/api/v1/tokens?page=${queryPage}&size=${querySize}`,
    )
    return {
      ...page,
      items: page.items
        .filter((token) => token.id !== undefined && token.name && token.tokenPrefix && token.createdAt)
        .map((token) => ({
          ...token,
          id: token.id!,
          name: token.name!,
          tokenPrefix: token.tokenPrefix!,
          createdAt: token.createdAt!,
        })),
    }
  },

  async createToken(request: CreateTokenRequest): Promise<CreateTokenResponse> {
    const token = await fetchJson<CreateTokenResponse>('/api/v1/tokens', {
      method: 'POST',
      headers: withCsrf({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
    if (!token.token || token.id === undefined || !token.name || !token.tokenPrefix || !token.createdAt) {
      throw new Error('Invalid token creation response')
    }
    return {
      ...token,
      token: token.token,
      id: token.id,
      name: token.name,
      tokenPrefix: token.tokenPrefix,
      createdAt: token.createdAt,
    }
  },

  async updateTokenExpiration(tokenId: number, expiresAt?: string): Promise<ApiToken> {
    return fetchJson<ApiToken>(`/api/v1/tokens/${tokenId}/expiration`, {
      method: 'PUT',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ expiresAt: expiresAt ?? '' }),
    })
  },

  async deleteToken(tokenId: number): Promise<void> {
    const { error, response } = await client.DELETE('/api/v1/tokens/{id}', {
      params: {
        path: {
          id: tokenId,
        },
      },
      headers: withCsrf(),
    } as never)

    if (response.status === 204) {
      return
    }

    const envelope = (error && isApiEnvelope<void>(error) ? error : null) as { msg?: string } | null
    if (!response.ok || error) {
      throw new ApiError(envelope?.msg || `HTTP ${response.status}`, response.status, envelope?.msg, envelope?.msg)
    }
  },
}

export const reviewApi = {
  async list(params: { status: string; namespaceId?: number; page?: number; size?: number; sortDirection?: 'ASC' | 'DESC' }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status)
    if (params.namespaceId !== undefined) {
      searchParams.set('namespaceId', String(params.namespaceId))
    }
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    searchParams.set('sortDirection', params.sortDirection ?? 'DESC')
    return fetchJson<{ items: ReviewTask[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/reviews?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<ReviewTask> {
    return fetchJson<ReviewTask>(`${WEB_API_PREFIX}/reviews/${id}`)
  },

  async getSkillDetail(id: number): Promise<ReviewSkillDetail> {
    return fetchJson<ReviewSkillDetail>(`${WEB_API_PREFIX}/reviews/${id}/skill-detail`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/reviews/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/reviews/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const promotionApi = {
  async submit(request: { sourceSkillId: number; sourceVersionId: number; targetNamespaceId: number }): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async list(params: { status?: PromotionStatus; page?: number; size?: number; sortBy?: PromotionSortBy; sortDirection?: PromotionSortDirection }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status ?? 'PENDING')
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    if (params.sortBy) {
      searchParams.set('sortBy', params.sortBy)
    }
    if (params.sortDirection) {
      searchParams.set('sortDirection', params.sortDirection)
    }
    return fetchJson<{ items: PromotionTask[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/promotions?${searchParams.toString()}`,
    )
  },

  async get(id: number): Promise<PromotionTask> {
    return fetchJson<PromotionTask>(`${WEB_API_PREFIX}/promotions/${id}`)
  },

  async approve(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },

  async reject(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`${WEB_API_PREFIX}/promotions/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const reportApi = {
  async submitSkillReport(namespace: string, slug: string, request: { reason: string; details?: string }): Promise<void> {
    const cleanNamespace = namespace.startsWith('@') ? namespace.slice(1) : namespace
    await fetchJson<void>(`${WEB_API_PREFIX}/skills/${cleanNamespace}/${encodeURIComponent(slug)}/reports`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },

  async listSkillReports(params: { status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('status', params.status ?? 'PENDING')
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: SkillReport[]; total: number; page: number; size: number }>(
      `/api/v1/admin/skill-reports?${searchParams.toString()}`,
    )
  },

  async resolveSkillReport(id: number, comment?: string, disposition: ReportDisposition = 'RESOLVE_ONLY'): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skill-reports/${id}/resolve`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment, disposition }),
    })
  },

  async dismissSkillReport(id: number, comment?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skill-reports/${id}/dismiss`, {
      method: 'POST',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const governanceApi = {
  async getSummary(): Promise<GovernanceSummary> {
    return fetchJson<GovernanceSummary>(`${WEB_API_PREFIX}/governance/summary`)
  },

  async getInbox(params: { type?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.type) searchParams.set('type', params.type)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceInboxItem>>(
      `${WEB_API_PREFIX}/governance/inbox?${searchParams.toString()}`,
    )
  },

  async getActivity(params: { page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceActivityItem>>(
      `${WEB_API_PREFIX}/governance/activity?${searchParams.toString()}`,
    )
  },

  async getNotifications(params: { page?: number; size?: number }): Promise<PagedResponse<GovernanceNotification>> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<PagedResponse<GovernanceNotification>>(`${WEB_API_PREFIX}/governance/notifications?${searchParams.toString()}`)
  },

  async markNotificationRead(id: number): Promise<GovernanceNotification> {
    return fetchJson<GovernanceNotification>(`${WEB_API_PREFIX}/governance/notifications/${id}/read`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async rebuildSearchIndex(): Promise<void> {
    await fetchJson<void>('/api/v1/admin/search/rebuild', {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },
}

export const meApi = {
  async getSkills(params?: { page?: number; size?: number; filter?: string; q?: string; namespace?: string }): Promise<{ items: SkillSummary[]; total: number; page: number; size: number }> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 10))
    if (params?.filter) {
      searchParams.set('filter', params.filter)
    }
    if (params?.q) {
      searchParams.set('q', params.q)
    }
    if (params?.namespace) {
      searchParams.set('namespace', params.namespace)
    }
    return fetchJson<{ items: SkillSummary[]; total: number; page: number; size: number }>(`${WEB_API_PREFIX}/me/skills?${searchParams.toString()}`)
  },

  async getStarsPage(params?: { page?: number; size?: number }): Promise<{ items: SkillSummary[]; total: number; page: number; size: number }> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 12))
    return fetchJson<{ items: SkillSummary[]; total: number; page: number; size: number }>(`${WEB_API_PREFIX}/me/stars?${searchParams.toString()}`)
  },

  async getStars(): Promise<SkillSummary[]> {
    const items: SkillSummary[] = []
    let page = 0
    const size = 100
    let hasMore = true

    while (hasMore) {
      const response = await meApi.getStarsPage({ page, size })
      items.push(...response.items)

      hasMore = (page + 1) * response.size < response.total && response.items.length > 0
      page += 1
    }

    return items
  },

  async getSubscriptionsPage(params?: { page?: number; size?: number }): Promise<{ items: SkillSummary[]; total: number; page: number; size: number }> {
    const searchParams = new URLSearchParams()
    searchParams.set('page', String(params?.page ?? 0))
    searchParams.set('size', String(params?.size ?? 12))
    return fetchJson<{ items: SkillSummary[]; total: number; page: number; size: number }>(`${WEB_API_PREFIX}/me/subscriptions?${searchParams.toString()}`)
  },

  async getSubscriptions(): Promise<SkillSummary[]> {
    const items: SkillSummary[] = []
    let page = 0
    const size = 100
    let hasMore = true

    while (hasMore) {
      const response = await meApi.getSubscriptionsPage({ page, size })
      items.push(...response.items)

      hasMore = (page + 1) * response.size < response.total && response.items.length > 0
      page++
    }

    return items
  },
}

export const profileApi = {
  async getProfile(): Promise<{
    displayName: string
    avatarUrl: string | null
    email: string | null
    pendingChanges: {
      status: string
      changes: Record<string, string>
      reviewComment: string | null
      createdAt: string
    } | null
    fieldPolicies: Record<string, { editable: boolean; requiresReview: boolean }>
  }> {
    return fetchJson('/api/v1/user/profile')
  },
  async updateProfile(request: Record<string, string>): Promise<{
    status: string
    appliedFields?: Record<string, string>
    pendingFields?: Record<string, string>
  }> {
    return fetchJson<{ status: string; appliedFields?: Record<string, string>; pendingFields?: Record<string, string> }>('/api/v1/user/profile', {
      method: 'PATCH',
      headers: await ensureCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify(request),
    })
  },
}

export const adminApi = {
  async getUsers(params: { search?: string; status?: string; page?: number; size?: number }) {
    const searchParams = new URLSearchParams()
    if (params.search) searchParams.set('search', params.search)
    if (params.status) searchParams.set('status', params.status)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    const response = await fetchJson<{
      items: Array<{
        id: string
        username: string
        email?: string
        platformRoles?: string[]
        status: string
        createdAt: string
      }>
      total: number
      page: number
      size: number
    }>(
      `/api/v1/admin/users?${searchParams.toString()}`,
    )
    return {
      ...response,
      items: response.items
        .filter((user) => user.id && user.username && user.status && user.createdAt)
        .map((user) => ({
          userId: user.id,
          username: user.username,
          email: user.email,
          platformRoles: user.platformRoles ?? [],
          status: user.status,
          createdAt: user.createdAt,
        })),
    }
  },

  async updateUserRole(userId: string, role: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/role`, {
      method: 'PUT',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ role }),
    })
  },

  async updateUserStatus(userId: string, status: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/status`, {
      method: 'PUT',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ status }),
    })
  },

  async approveUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async disableUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/disable`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async enableUser(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/enable`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async triggerPasswordReset(userId: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/users/${userId}/password-reset`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async getAuditLogs(params: {
    action?: string
    userId?: string
    requestId?: string
    ipAddress?: string
    resourceType?: string
    resourceId?: string
    startTime?: string
    endTime?: string
    page?: number
    size?: number
  }) {
    const searchParams = new URLSearchParams()
    if (params.action) searchParams.set('action', params.action)
    if (params.userId) searchParams.set('userId', params.userId)
    if (params.requestId) searchParams.set('requestId', params.requestId)
    if (params.ipAddress) searchParams.set('ipAddress', params.ipAddress)
    if (params.resourceType) searchParams.set('resourceType', params.resourceType)
    if (params.resourceId) searchParams.set('resourceId', params.resourceId)
    if (params.startTime) searchParams.set('startTime', params.startTime)
    if (params.endTime) searchParams.set('endTime', params.endTime)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    return fetchJson<{ items: AuditLogItem[]; total: number; page: number; size: number }>(
      `/api/v1/admin/audit-logs?${searchParams.toString()}`,
    )
  },

  async hideSkill(skillId: number, reason?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/${skillId}/hide`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ reason }),
    })
  },

  async unhideSkill(skillId: number): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/${skillId}/unhide`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async yankVersion(versionId: number, reason?: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/skills/versions/${versionId}/yank`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ reason }),
    })
  },

  async getProfileReviews(params: { status?: string; page?: number; size?: number; sortDirection?: 'ASC' | 'DESC' }) {
    const searchParams = new URLSearchParams()
    if (params.status) searchParams.set('status', params.status)
    searchParams.set('page', String(params.page ?? 0))
    searchParams.set('size', String(params.size ?? 20))
    searchParams.set('sortDirection', params.sortDirection ?? 'DESC')
    const response = await fetchJson<{
      items: Array<{
        id: number
        userId: string
        username: string
        currentDisplayName: string | null
        requestedDisplayName: string | null
        status: string
        machineResult: string | null
        reviewerId: string | null
        reviewerName: string | null
        reviewComment: string | null
        createdAt: string
        reviewedAt: string | null
      }>
      total: number
      page: number
      size: number
    }>(`/api/v1/admin/profile-reviews?${searchParams}`)

    return {
      ...response,
      totalElements: response.total,
      totalPages: response.size > 0 ? Math.ceil(response.total / response.size) : 0,
    }
  },

  async approveProfileReview(id: number): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/profile-reviews/${id}/approve`, {
      method: 'POST',
      headers: getCsrfHeaders(),
    })
  },

  async rejectProfileReview(id: number, comment: string): Promise<void> {
    await fetchJson<void>(`/api/v1/admin/profile-reviews/${id}/reject`, {
      method: 'POST',
      headers: getCsrfHeaders({ 'Content-Type': 'application/json' }),
      body: JSON.stringify({ comment }),
    })
  },
}

export const notificationApi = {
  async list(params: { page?: number; size?: number; category?: string }) {
    const searchParams = new URLSearchParams()
    if (params.page !== undefined) searchParams.set('page', String(params.page))
    if (params.size !== undefined) searchParams.set('size', String(params.size))
    if (params.category) searchParams.set('category', params.category)
    return fetchJson<{ items: NotificationItem[]; total: number; page: number; size: number }>(
      `${WEB_API_PREFIX}/notifications?${searchParams.toString()}`,
    )
  },

  async getUnreadCount() {
    return fetchJson<NotificationUnreadCount>(`${WEB_API_PREFIX}/notifications/unread-count`)
  },

  async markRead(id: number) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notifications/${id}/read`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  },

  async markAllRead() {
    return fetchJson<{ count: number }>(`${WEB_API_PREFIX}/notifications/read-all`, {
      method: 'PUT',
      headers: getCsrfHeaders(),
    })
  },

  async deleteRead(id: number) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notifications/${id}`, {
      method: 'DELETE',
      headers: getCsrfHeaders(),
    })
  },

  async getPreferences() {
    return fetchJson<NotificationPreferenceItem[]>(`${WEB_API_PREFIX}/notification-preferences`)
  },

  async updatePreferences(preferences: NotificationPreferenceItem[]) {
    await fetchJson<void>(`${WEB_API_PREFIX}/notification-preferences`, {
      method: 'PUT',
      headers: getCsrfHeaders({
        'Content-Type': 'application/json',
      }),
      body: JSON.stringify({ preferences }),
    })
  },
}

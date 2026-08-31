import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Cable,
  Check,
  ChevronRight,
  FileText,
  GitCompare,
  History,
  Loader2,
  MessageSquare,
  PackageOpen,
  Play,
  Save,
  Search,
  Send,
  ShieldAlert,
  UploadCloud,
  X,
} from 'lucide-react'
import { meApi, namespaceApi, skillLifecycleApi, workbenchApi } from '@/api/client'
import type {
  CreateWorkbenchSessionRequest,
  ManagedNamespace,
  SkillSummary,
  SkillVersion,
  WorkbenchFile,
  WorkbenchFileContent,
  WorkbenchMcpBinding,
  WorkbenchMcpCatalogItem,
  WorkbenchMode,
  WorkbenchPackagePreview,
  WorkbenchPublishResult,
  WorkbenchRuntimeConfig,
  PublishWorkbenchPackageRequest,
  WorkbenchRuntimeRunResponse,
  WorkbenchRuntimeStreamEvent,
  WorkbenchSession,
  WorkbenchSessionEvent,
  WorkbenchToolApproval,
} from '@/api/types'
import { MarkdownRenderer } from '@/features/skill/markdown-renderer'
import { Button } from '@/shared/ui/button'
import { Input } from '@/shared/ui/input'
import { Label } from '@/shared/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/shared/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { Textarea } from '@/shared/ui/textarea'
import { cn } from '@/shared/lib/utils'

const DEFAULT_CONTENT_TYPE = 'text/markdown; charset=utf-8'
const NON_TEXT_WARNING = '当前文件不是文本文件，Phase 4 仅支持 UTF-8 文本编辑。'
const MCP_RUNTIME_NOTE = 'MCP 预选会作为后续对话的工具偏好保存到当前会话；运行时调用能力以服务端适配器接入状态为准。'
const EMPTY_SESSIONS: WorkbenchSession[] = []
const ACTIVE_RUN_STALE_AFTER_MS = 15 * 60 * 1000

const TEXT_EXTENSIONS = new Set([
  '.css',
  '.csv',
  '.env',
  '.html',
  '.cjs',
  '.cfg',
  '.dtd',
  '.java',
  '.js',
  '.json',
  '.go',
  '.rs',
  '.sql',
  '.ps1',
  '.md',
  '.mjs',
  '.py',
  '.rb',
  '.r',
  '.ini',
  '.kt',
  '.lua',
  '.bat',
  '.bash',
  '.zsh',
  '.sh',
  '.svg',
  '.toml',
  '.ts',
  '.tsx',
  '.txt',
  '.xml',
  '.xsd',
  '.xsl',
  '.yaml',
  '.yml',
])

function hasTextExtension(path: string) {
  const normalized = path.toLowerCase()
  return [...TEXT_EXTENSIONS].some((extension) => normalized.endsWith(extension))
}

function isEditableTextFile(file: WorkbenchFile) {
  const contentType = file.contentType?.toLowerCase() ?? ''
  return contentType.startsWith('text/')
    || contentType.includes('json')
    || contentType.includes('javascript')
    || contentType.includes('typescript')
    || contentType.includes('yaml')
    || contentType.includes('xml')
    || hasTextExtension(file.path)
}

function formatBytes(sizeBytes: number) {
  if (!Number.isFinite(sizeBytes) || sizeBytes < 0) return '0 B'
  if (sizeBytes < 1024) return `${sizeBytes} B`
  if (sizeBytes < 1024 * 1024) return `${(sizeBytes / 1024).toFixed(1)} KB`
  return `${(sizeBytes / 1024 / 1024).toFixed(1)} MB`
}

function statusClassName(status: string) {
  switch (status) {
    case 'ADDED':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'MODIFIED':
      return 'border-amber-200 bg-amber-50 text-amber-700'
    case 'DELETED':
      return 'border-red-200 bg-red-50 text-red-700'
    case 'UNCHANGED':
      return 'border-slate-200 bg-slate-50 text-slate-600'
    default:
      return 'border-border bg-secondary text-muted-foreground'
  }
}

function workbenchModeLabel(mode: string) {
  switch (mode) {
    case 'CREATE_SKILL':
      return '创建技能'
    case 'UPDATE_SKILL':
      return '更新技能'
    default:
      return mode
  }
}

function sessionStatusLabel(status: string) {
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'RUNNING':
      return '进行中'
    case 'WAITING_APPROVAL':
      return '等待确认'
    case 'READY_FOR_REVIEW':
      return '待确认'
    case 'PUBLISHING':
      return '发布中'
    case 'PUBLISHED':
      return '已发布'
    case 'EXPIRED':
      return '已过期'
    case 'CANCELLED':
      return '已取消'
    case 'FAILED':
      return '失败'
    default:
      return status
  }
}

function isFreshActiveRun(activeRun?: WorkbenchRuntimeRunResponse | null) {
  if (activeRun?.status !== 'STARTED') return false
  if (!activeRun.startedAt) return true
  const startedAt = Date.parse(activeRun.startedAt)
  if (Number.isNaN(startedAt)) return true
  return Date.now() - startedAt <= ACTIVE_RUN_STALE_AFTER_MS
}

function isStaleActiveRun(activeRun?: WorkbenchRuntimeRunResponse | null) {
  return activeRun?.status === 'STARTED' && !isFreshActiveRun(activeRun)
}

function sessionActivityLabel(status: string, activeRun?: WorkbenchRuntimeRunResponse | null) {
  if (isFreshActiveRun(activeRun)) {
    return '正在回复'
  }
  if (isStaleActiveRun(activeRun)) {
    return '可能中断'
  }
  switch (status) {
    case 'DRAFT':
      return '草稿'
    case 'RUNNING':
      return '可继续'
    case 'WAITING_APPROVAL':
      return '需确认'
    case 'READY_FOR_REVIEW':
      return '待发布'
    case 'PUBLISHING':
      return '发布中'
    case 'PUBLISHED':
      return '已发布'
    case 'EXPIRED':
      return '已过期'
    case 'CANCELLED':
      return '已取消'
    case 'FAILED':
      return '失败'
    default:
      return status
  }
}

function sessionActivityClassName(status: string, activeRun?: WorkbenchRuntimeRunResponse | null) {
  if (isFreshActiveRun(activeRun)) {
    return 'border-blue-200 bg-blue-50 text-blue-700'
  }
  if (isStaleActiveRun(activeRun)) {
    return 'border-amber-200 bg-amber-50 text-amber-700'
  }
  switch (status) {
    case 'WAITING_APPROVAL':
    case 'READY_FOR_REVIEW':
    case 'PUBLISHING':
      return 'border-amber-200 bg-amber-50 text-amber-700'
    case 'PUBLISHED':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'FAILED':
    case 'EXPIRED':
      return 'border-red-200 bg-red-50 text-red-700'
    case 'CANCELLED':
      return 'border-slate-200 bg-slate-50 text-slate-600'
    case 'RUNNING':
    case 'DRAFT':
    default:
      return 'border-border bg-secondary text-muted-foreground'
  }
}

function runtimeStatusLabel(status: string) {
  switch (status) {
    case 'STARTED':
      return '响应中'
    case 'CANCELLED':
      return '已取消'
    case 'FAILED':
      return '失败'
    case 'COMPLETED':
      return '已完成'
    default:
      return status
  }
}

function eventTypeLabel(type: string) {
  switch (type) {
    case 'SESSION_CREATED':
      return '会话创建'
    case 'FILE_WRITTEN':
      return '文件保存'
    case 'FILE_DELETED':
      return '文件删除'
    case 'SOURCE_IMPORTED':
      return '源版本导入'
    case 'STATUS_CHANGED':
      return '状态变更'
    case 'RUNTIME_MESSAGE':
      return '模型消息'
    case 'RUNTIME_FILE_CHANGED':
      return '模型修改文件'
    case 'USER_MESSAGE':
      return '用户消息'
    case 'MODEL_MESSAGE':
      return '模型回复'
    case 'FILE_CHANGED':
      return '文件变更'
    case 'TOOL_CALL':
      return '工具调用'
    case 'APPROVAL_REQUIRED':
      return '需要确认'
    case 'APPROVAL_DECIDED':
      return '确认结果'
    case 'ERROR':
      return '错误'
    case 'AUDIT':
      return '审计事件'
    default:
      return type
  }
}

function diffStatusLabel(status: string) {
  switch (status) {
    case 'ADDED':
      return '新增'
    case 'MODIFIED':
      return '已修改'
    case 'DELETED':
      return '已删除'
    case 'UNCHANGED':
      return '未变化'
    default:
      return status
  }
}

function payloadKeyLabel(key: string) {
  switch (key) {
    case 'mode':
      return '模式'
    case 'path':
      return '文件路径'
    case 'status':
      return '状态'
    case 'action':
      return '动作'
    default:
      return key
  }
}

function payloadValueLabel(value: unknown): unknown {
  if (typeof value !== 'string') return value
  const mode = workbenchModeLabel(value)
  if (mode !== value) return mode
  const sessionStatus = sessionStatusLabel(value)
  if (sessionStatus !== value) return sessionStatus
  const runtimeStatus = runtimeStatusLabel(value)
  if (runtimeStatus !== value) return runtimeStatus
  return value
}

function localizePayload(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(localizePayload)
  }
  if (typeof value === 'object' && value !== null) {
    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [payloadKeyLabel(key), localizePayload(payloadValueLabel(entry))]),
    )
  }
  return payloadValueLabel(value)
}

function prettyPayload(payloadJson: string) {
  try {
    return JSON.stringify(localizePayload(JSON.parse(payloadJson)), null, 2)
  } catch {
    return payloadJson
  }
}

type WorkbenchChatMessage = {
  id: number | string
  runId?: string
  sourceUserEventId?: number
  role: 'user' | 'assistant' | 'system'
  label: string
  createdAt: string
  content: string
  pending?: boolean
  phase?: 'thinking' | 'tool' | 'final' | 'error'
  thinkingContent?: string
  finalContent?: string
  toolItems?: Array<{
    id: string
    label: string
    content: string
  }>
  sourceUserContent?: string
}

function parsePayloadObject(payloadJson: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(payloadJson)
    return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function payloadMessage(payloadJson: string) {
  const payload = parsePayloadObject(payloadJson)
  const candidates = [payload.message, payload.text, payload.content, payload.summary]
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim()) return candidate.trim()
  }
  return ''
}

function payloadText(payloadJson: string, keys: string[]) {
  const payload = parsePayloadObject(payloadJson)
  for (const key of keys) {
    const value = payload[key]
    if (typeof value === 'string' && value.trim()) return value.trim()
    if (typeof value === 'number') return String(value)
  }
  return ''
}

function payloadRunId(payloadJson: string) {
  const payload = parsePayloadObject(payloadJson)
  return typeof payload.runId === 'string' && payload.runId.trim() ? payload.runId : undefined
}

function payloadSourceUserEventId(payloadJson: string) {
  const payload = parsePayloadObject(payloadJson)
  return typeof payload.sourceUserEventId === 'number' ? payload.sourceUserEventId : undefined
}

function payloadSourceUserContent(payloadJson: string) {
  const payload = parsePayloadObject(payloadJson)
  return typeof payload.sourceUserContent === 'string' && payload.sourceUserContent.trim()
    ? payload.sourceUserContent
    : undefined
}

function chatMessageIdentity(item: WorkbenchChatMessage) {
  return `${item.role}:${String(item.id)}`
}

function anchorAssistantReplies(messages: WorkbenchChatMessage[]) {
  const sourceUserEventIdQueues = new Map<number, WorkbenchChatMessage[]>()
  const sourceUserContentQueues = new Map<string, WorkbenchChatMessage[]>()
  const mainMessages: WorkbenchChatMessage[] = []

  for (const item of messages) {
    if (item.role === 'assistant' && (item.sourceUserEventId || item.sourceUserContent)) {
      if (item.sourceUserEventId) {
        const queue = sourceUserEventIdQueues.get(item.sourceUserEventId) ?? []
        queue.push(item)
        sourceUserEventIdQueues.set(item.sourceUserEventId, queue)
        continue
      }
      const sourceContent = normalizeChatContent(item.sourceUserContent ?? '')
      if (sourceContent) {
        const queue = sourceUserContentQueues.get(sourceContent) ?? []
        queue.push(item)
        sourceUserContentQueues.set(sourceContent, queue)
        continue
      }
    }
    mainMessages.push(item)
  }

  const anchored: WorkbenchChatMessage[] = []
  const consumed = new Set<string>()
  const flushQueue = (queue?: WorkbenchChatMessage[]) => {
    if (!queue) return
    for (const reply of queue) {
      const identity = chatMessageIdentity(reply)
      if (consumed.has(identity)) continue
      anchored.push(reply)
      consumed.add(identity)
    }
  }

  for (const item of mainMessages) {
    anchored.push(item)
    if (item.role !== 'user') continue
    if (typeof item.id === 'number') {
      flushQueue(sourceUserEventIdQueues.get(item.id))
    }
    flushQueue(sourceUserContentQueues.get(normalizeChatContent(item.content)))
  }

  for (const queue of sourceUserEventIdQueues.values()) {
    flushQueue(queue)
  }
  for (const queue of sourceUserContentQueues.values()) {
    flushQueue(queue)
  }

  return anchored
}

function toChatMessage(event: WorkbenchSessionEvent): WorkbenchChatMessage | null {
  const content = payloadMessage(event.payloadJson)
  switch (event.type) {
    case 'USER_MESSAGE':
      if (!content) return null
      return { id: event.eventId, role: 'user', label: '你', createdAt: event.createdAt, content }
    case 'MODEL_MESSAGE': {
      if (!content) return null
      const payload = parsePayloadObject(event.payloadJson)
      return {
        id: event.eventId,
        runId: typeof payload.runId === 'string' ? payload.runId : undefined,
        sourceUserEventId: payloadSourceUserEventId(event.payloadJson),
        role: 'assistant',
        label: '工作台',
        createdAt: event.createdAt,
        content,
        phase: 'final',
        finalContent: content,
        sourceUserContent: payloadSourceUserContent(event.payloadJson),
      }
    }
    case 'TOOL_CALL': {
      const toolName = payloadText(event.payloadJson, ['toolName', 'tool', 'name', 'action'])
      const toolContent = content || (toolName ? `调用工具：${toolName}` : '正在调用工具')
      return {
        id: event.eventId,
        runId: payloadRunId(event.payloadJson),
        sourceUserEventId: payloadSourceUserEventId(event.payloadJson),
        role: 'assistant',
        label: '工具',
        createdAt: event.createdAt,
        content: toolContent,
        phase: 'tool',
        sourceUserContent: payloadSourceUserContent(event.payloadJson),
      }
    }
    case 'APPROVAL_REQUIRED': {
      const toolName = payloadText(event.payloadJson, ['toolName', 'tool', 'name'])
      return {
        id: event.eventId,
        runId: payloadRunId(event.payloadJson),
        sourceUserEventId: payloadSourceUserEventId(event.payloadJson),
        role: 'assistant',
        label: '工具确认',
        createdAt: event.createdAt,
        content: toolName ? `工具 ${toolName} 需要确认后继续。` : '有工具操作需要确认后继续。',
        phase: 'tool',
        sourceUserContent: payloadSourceUserContent(event.payloadJson),
      }
    }
    case 'FILE_CHANGED': {
      const path = payloadText(event.payloadJson, ['path', 'filePath', 'file'])
      const fileContent = content || (path ? `已更新文件：${path}` : '已更新工作区文件')
      return { id: event.eventId, runId: payloadRunId(event.payloadJson), role: 'assistant', label: '文件', createdAt: event.createdAt, content: fileContent, phase: 'tool' }
    }
    case 'ERROR':
      if (!content) return null
      return { id: event.eventId, runId: payloadRunId(event.payloadJson), role: 'system', label: '系统', createdAt: event.createdAt, content, phase: 'error' }
    default:
      return null
  }
}

function runtimeStreamEventToChatMessage(event: WorkbenchRuntimeStreamEvent, id: string): WorkbenchChatMessage | null {
  if (event.event !== 'runtime_event') return null
  const type = typeof event.data.type === 'string' ? event.data.type : ''
  const payloadJson = typeof event.data.payloadJson === 'string' ? event.data.payloadJson : '{}'
  const eventType = type === 'FILE_WRITTEN' || type === 'FILE_DELETED' ? 'FILE_CHANGED' : type
  if (eventType === 'MODEL_MESSAGE' || eventType === 'ERROR') return null
  const chatMessage = toChatMessage({
    eventId: 0,
    type: eventType,
    createdAt: new Date().toISOString(),
    payloadJson,
  })
  return chatMessage ? { ...chatMessage, id } : null
}

function streamRunResponse(data: Record<string, unknown>): WorkbenchRuntimeRunResponse {
  return {
    runId: typeof data.runId === 'string' ? data.runId : 'stream',
    status: typeof data.status === 'string' ? data.status : 'COMPLETED',
    message: typeof data.message === 'string' ? data.message : null,
    eventCount: typeof data.eventCount === 'number' ? data.eventCount : 0,
  }
}

function formatChatTime(value: string) {
  if (value === 'sending') return '刚刚'
  if (value === 'streaming') return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

function normalizeChatContent(value: string) {
  return value.trim().replace(/\r\n/g, '\n').replace(/[ \t]+\n/g, '\n')
}

function formatRedactedArguments(argumentsRedactedJson: Record<string, unknown>) {
  const entries = Object.entries(argumentsRedactedJson)
  if (entries.length === 0) return '无参数'
  return JSON.stringify(Object.fromEntries(entries), null, 2)
}

function riskLevelLabel(riskLevel: string) {
  switch (riskLevel) {
    case 'READ_ONLY':
      return '只读'
    case 'MUTATING':
      return '需确认'
    case 'UNKNOWN':
      return '未知风险'
    case 'DENIED':
      return '已拒绝'
    default:
      return riskLevel || '未知风险'
  }
}

function riskLevelClassName(riskLevel: string) {
  switch (riskLevel) {
    case 'READ_ONLY':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'MUTATING':
    case 'UNKNOWN':
      return 'border-amber-200 bg-amber-50 text-amber-700'
    case 'DENIED':
      return 'border-red-200 bg-red-50 text-red-700'
    default:
      return 'border-border bg-secondary text-muted-foreground'
  }
}

function validationStatusClassName(status: string) {
  switch (status) {
    case 'PASS':
    case 'PASSED':
    case 'READY':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700'
    case 'FAIL':
    case 'FAILED':
    case 'ERROR':
      return 'border-red-200 bg-red-50 text-red-700'
    default:
      return 'border-amber-200 bg-amber-50 text-amber-700'
  }
}

function visibilityLabel(visibility: string) {
  switch (visibility) {
    case 'PUBLIC':
      return '公开'
    case 'NAMESPACE_ONLY':
      return '命名空间可见'
    case 'PRIVATE':
      return '私有'
    default:
      return visibility
  }
}

function enabledBindingServerIds(bindings: WorkbenchMcpBinding[]) {
  return bindings
    .filter((binding) => binding.status === 'ENABLED')
    .map((binding) => binding.serverId)
}

function stableSelectionKey(values: string[]) {
  return values.slice().sort().join('\n')
}

function mcpUnavailableReason(server: WorkbenchMcpCatalogItem) {
  if (!server.enabled) return '服务已停用，不能作为本次会话工具。'
  if (!server.runtimeCandidate) return '服务缺少运行时可调用配置，暂不能作为模型工具。'
  return ''
}

function displayError(error: unknown) {
  return error instanceof Error && error.message ? error.message : '操作失败，请稍后重试。'
}

function buildCreateRequest(form: {
  namespace: string
  mode: WorkbenchMode
  sourceNamespace: string
  sourceSlug: string
  sourceVersion: string
  targetSlug: string
  targetVersion: string
  expiresInHours: string
}): CreateWorkbenchSessionRequest {
  const request: CreateWorkbenchSessionRequest = {
    namespace: form.namespace.trim(),
    mode: form.mode,
    targetSlug: form.targetSlug.trim(),
    targetVersion: form.targetVersion.trim(),
  }
  const expiresInHours = Number.parseInt(form.expiresInHours, 10)
  if (Number.isFinite(expiresInHours)) {
    request.expiresInHours = expiresInHours
  }
  if (form.mode === 'UPDATE_SKILL') {
    request.sourceSkill = {
      namespace: form.sourceNamespace.trim(),
      slug: form.sourceSlug.trim(),
      version: form.sourceVersion.trim(),
    }
  }
  return request
}

function namespaceLabel(namespace: ManagedNamespace) {
  return namespace.displayName && namespace.displayName !== namespace.slug
    ? `${namespace.displayName} (@${namespace.slug})`
    : `@${namespace.slug}`
}

function skillLabel(skill: SkillSummary) {
  return skill.displayName && skill.displayName !== skill.slug
    ? `${skill.displayName} (${skill.slug})`
    : skill.slug
}

function sessionTitle(session: WorkbenchSession) {
  return session.targetSlug || `会话 #${session.id}`
}

function sessionSubtitle(session: WorkbenchSession) {
  return `${workbenchModeLabel(session.mode)} · ${session.targetVersion}`
}

function sessionAvatarText(session: WorkbenchSession) {
  const source = session.targetSlug || String(session.id)
  return source.slice(0, 2).toUpperCase()
}

function runtimeConfigTitle(config?: WorkbenchRuntimeConfig) {
  if (!config) return '模型配置读取中'
  return config.modelConfigured ? `${config.modelProvider} · ${config.modelName}` : '模型未配置'
}

function runtimeConfigTone(config?: WorkbenchRuntimeConfig) {
  return config?.modelConfigured
    ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
    : 'border-amber-200 bg-amber-50 text-amber-800'
}

function sessionSearchText(session: WorkbenchSession) {
  return [
    String(session.id),
    session.targetSlug,
    session.targetVersion,
    workbenchModeLabel(session.mode),
    sessionStatusLabel(session.status),
    sessionActivityLabel(session.status, session.activeRun),
  ].join(' ').toLowerCase()
}

function WorkbenchEventCard({ event }: { event: WorkbenchSessionEvent }) {
  return (
    <div className="rounded-lg border border-border/70 bg-background p-3">
      <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
        <span className="font-medium">{eventTypeLabel(event.type)}</span>
        <span className="text-xs text-muted-foreground">#{event.eventId}</span>
      </div>
      <p className="mt-1 text-xs text-muted-foreground">{event.createdAt}</p>
      <details className="mt-2">
        <summary className="cursor-pointer text-xs font-medium text-muted-foreground hover:text-foreground">
          查看详情
        </summary>
        <pre className="mt-2 max-h-32 overflow-auto rounded-md bg-secondary/40 p-2 text-xs text-muted-foreground">
          {prettyPayload(event.payloadJson)}
        </pre>
      </details>
    </div>
  )
}

function bumpPatchVersion(version: string) {
  const match = version.match(/^(\d+)\.(\d+)\.(\d+)(.*)$/)
  if (!match) return '0.1.0'
  return `${match[1]}.${match[2]}.${Number(match[3]) + 1}${match[4] ?? ''}`
}

function versionChoices(sourceVersion: string, mode: WorkbenchMode) {
  if (mode === 'CREATE_SKILL') {
    return ['0.1.0', '1.0.0']
  }
  const patch = bumpPatchVersion(sourceVersion)
  return Array.from(new Set([patch, '0.1.0', '1.0.0']))
}

export function WorkbenchPage() {
  const queryClient = useQueryClient()
  const [activeSessionId, setActiveSessionId] = useState<number | null>(null)
  const [activeTab, setActiveTab] = useState('chat')
  const [mode, setMode] = useState<WorkbenchMode>('CREATE_SKILL')
  const [namespace, setNamespace] = useState('')
  const [targetSlug, setTargetSlug] = useState('')
  const [targetVersion, setTargetVersion] = useState('0.1.0')
  const [expiresInHours, setExpiresInHours] = useState('24')
  const [sourceNamespace, setSourceNamespace] = useState('')
  const [sourceSlug, setSourceSlug] = useState('')
  const [sourceVersion, setSourceVersion] = useState('')
  const [showSessionSetup, setShowSessionSetup] = useState(false)
  const [showMcpCatalog, setShowMcpCatalog] = useState(false)
  const [sessionSearch, setSessionSearch] = useState('')
  const [sessionLimit, setSessionLimit] = useState(20)
  const [selectedFile, setSelectedFile] = useState<WorkbenchFile | null>(null)
  const [fileContent, setFileContent] = useState<WorkbenchFileContent | null>(null)
  const [editorContent, setEditorContent] = useState('')
  const [fileWarning, setFileWarning] = useState('')
  const [message, setMessage] = useState('')
  const [pendingChatMessages, setPendingChatMessages] = useState<WorkbenchChatMessage[]>([])
  const [lastRun, setLastRun] = useState<WorkbenchRuntimeRunResponse | null>(null)
  const [operationError, setOperationError] = useState('')
  const [selectedMcpServerIds, setSelectedMcpServerIds] = useState<string[]>([])
  const [packagePreview, setPackagePreview] = useState<WorkbenchPackagePreview | null>(null)
  const [packagePreviewSessionId, setPackagePreviewSessionId] = useState<number | null>(null)
  const [publishResult, setPublishResult] = useState<WorkbenchPublishResult | null>(null)
  const [packageVisibility, setPackageVisibility] = useState<PublishWorkbenchPackageRequest['visibility']>('PRIVATE')
  const chatEndRef = useRef<HTMLDivElement | null>(null)
  const chatScrollRef = useRef<HTMLDivElement | null>(null)

  const sessionQuery = useQuery({
    queryKey: ['workbench', 'session', activeSessionId],
    queryFn: () => workbenchApi.getSession(activeSessionId!),
    enabled: activeSessionId !== null,
  })

  const sessionsQuery = useQuery({
    queryKey: ['workbench', 'sessions', sessionLimit],
    queryFn: () => workbenchApi.listSessions({ limit: sessionLimit }),
  })

  const runtimeConfigQuery = useQuery({
    queryKey: ['workbench', 'runtime-config'],
    queryFn: () => workbenchApi.getRuntimeConfig(),
    staleTime: 60_000,
  })

  const namespacesQuery = useQuery({
    queryKey: ['workbench', 'namespaces'],
    queryFn: () => namespaceApi.listMine(),
  })

  const sourceSkillsQuery = useQuery({
    queryKey: ['workbench', 'source-skills', namespace],
    queryFn: () => meApi.getSkills({ namespace, size: 100 }),
    enabled: mode === 'UPDATE_SKILL' && !!namespace,
  })

  const sourceVersionsQuery = useQuery({
    queryKey: ['workbench', 'source-versions', namespace, sourceSlug],
    queryFn: () => skillLifecycleApi.listVersions(namespace, sourceSlug, { size: 100 }),
    enabled: mode === 'UPDATE_SKILL' && !!namespace && !!sourceSlug,
  })

  const filesQuery = useQuery({
    queryKey: ['workbench', 'files', activeSessionId],
    queryFn: () => workbenchApi.listFiles(activeSessionId!),
    enabled: activeSessionId !== null,
  })

  const eventsQuery = useQuery({
    queryKey: ['workbench', 'events', activeSessionId],
    queryFn: () => workbenchApi.listEvents(activeSessionId!, { limit: 200 }),
    enabled: activeSessionId !== null,
    refetchInterval: activeSessionId !== null ? (lastRun?.status === 'STARTED' ? 800 : 2500) : false,
  })

  const diffQuery = useQuery({
    queryKey: ['workbench', 'diff', activeSessionId],
    queryFn: () => workbenchApi.getDiff(activeSessionId!),
    enabled: activeSessionId !== null,
  })

  const mcpBindingsQuery = useQuery({
    queryKey: ['workbench', 'mcp-bindings', activeSessionId],
    queryFn: () => workbenchApi.listMcpBindings(activeSessionId!),
    enabled: activeSessionId !== null,
  })

  const mcpCatalogQuery = useQuery({
    queryKey: ['workbench', 'mcp-catalog', activeSessionId],
    queryFn: () => workbenchApi.listMcpCatalog(activeSessionId!, { page: 0, size: 20 }),
    enabled: activeSessionId !== null && (showMcpCatalog || enabledBindingServerIds(mcpBindingsQuery.data ?? []).length > 0),
    retry: false,
    meta: { skipGlobalErrorHandler: true },
  })

  const approvalsQuery = useQuery({
    queryKey: ['workbench', 'approvals', activeSessionId],
    queryFn: () => workbenchApi.listApprovals(activeSessionId!),
    enabled: activeSessionId !== null,
    refetchInterval: activeSessionId !== null ? (lastRun?.status === 'STARTED' ? 1000 : 5000) : false,
  })

  const activeSession = sessionQuery.data
  const sessionActiveRun = activeSession?.activeRun ?? null
  const activeRuntimeRun = isFreshActiveRun(lastRun)
    ? lastRun
    : isFreshActiveRun(sessionActiveRun)
      ? sessionActiveRun
      : null
  const runtimeConfig = runtimeConfigQuery.data
  const recentSessions = sessionsQuery.data ?? EMPTY_SESSIONS
  const filteredRecentSessions = useMemo(() => {
    const keyword = sessionSearch.trim().toLowerCase()
    if (!keyword) return recentSessions
    return recentSessions.filter((session) => sessionSearchText(session).includes(keyword))
  }, [recentSessions, sessionSearch])
  const namespaces = useMemo(() => namespacesQuery.data ?? [], [namespacesQuery.data])
  const sourceSkills = useMemo(() => sourceSkillsQuery.data?.items ?? [], [sourceSkillsQuery.data?.items])
  const sourceVersions = useMemo(() => sourceVersionsQuery.data?.items ?? [], [sourceVersionsQuery.data?.items])
  const targetVersionChoices = useMemo(() => versionChoices(sourceVersion, mode), [sourceVersion, mode])
  const files = filesQuery.data ?? []
  const events = useMemo(() => eventsQuery.data ?? [], [eventsQuery.data])
  const chatMessages = useMemo(() => events.map(toChatMessage).filter((item): item is WorkbenchChatMessage => item !== null), [events])
  const latestUserEventId = useMemo(() => events
    .filter((event) => event.type === 'USER_MESSAGE')
    .reduce((latest, event) => Math.max(latest, event.eventId), 0), [events])
  const hasTerminalRuntimeEventAfterLatestUser = useMemo(() => events.some((event) =>
    event.eventId > latestUserEventId
    && ['MODEL_MESSAGE', 'ERROR', 'APPROVAL_REQUIRED'].includes(event.type),
  ), [events, latestUserEventId])
  const hasPendingUserMessage = pendingChatMessages.some((item) => item.pending)
  const hasPendingAssistantProcess = pendingChatMessages.some((item) => item.role === 'assistant' && item.pending)
  const shouldShowThinkingMessage = !hasPendingAssistantProcess && ((hasPendingUserMessage && (!lastRun || lastRun.status === 'STARTED'))
    || (activeRuntimeRun?.status === 'STARTED' && !hasTerminalRuntimeEventAfterLatestUser)
  )
  const visibleChatMessages = useMemo(() => {
    const pendingAssistantRunIds = new Set(
      pendingChatMessages
        .filter((item) => item.role === 'assistant' && item.runId)
        .map((item) => item.runId as string),
    )
    const pendingAssistantFinalContents = (
      pendingChatMessages
        .filter((item) => item.role === 'assistant' && !item.pending && item.finalContent)
        .map((item) => ({
          finalContent: normalizeChatContent(item.finalContent ?? ''),
          sourceUserContent: normalizeChatContent(item.sourceUserContent ?? ''),
        }))
    )
    const latestPendingUserEventIds = new Map<string, number>()
    for (const item of chatMessages) {
      if (item.role !== 'user' || typeof item.id !== 'number') continue
      const userContent = normalizeChatContent(item.content)
      if (!pendingAssistantFinalContents.some((pending) => pending.sourceUserContent === userContent)) continue
      latestPendingUserEventIds.set(userContent, item.id)
    }
    const consumedLegacyAssistantKeys = new Set<string>()
    const seenSystemErrors = new Set<string>()
    let previousUserContent = ''
    let previousUserEventId: number | undefined
    const dedupedChatMessages = chatMessages.filter((item) => {
      if (item.role === 'user') {
        previousUserContent = normalizeChatContent(item.content)
        previousUserEventId = typeof item.id === 'number' ? item.id : undefined
        return true
      }
      const matchedLegacyPending = !item.runId && item.finalContent
        ? pendingAssistantFinalContents.find((pending) => {
            if (!pending.sourceUserContent || pending.sourceUserContent !== previousUserContent) return false
            if (latestPendingUserEventIds.get(pending.sourceUserContent) !== previousUserEventId) return false
            if (pending.finalContent !== normalizeChatContent(item.finalContent ?? '')) return false
            const legacyKey = `${pending.sourceUserContent}\n${pending.finalContent}`
            if (consumedLegacyAssistantKeys.has(legacyKey)) return false
            consumedLegacyAssistantKeys.add(legacyKey)
            return true
          })
        : undefined
      if (item.role === 'assistant' && item.phase === 'tool' && item.runId && pendingAssistantRunIds.has(item.runId)) {
        return false
      }
      if (
        item.role === 'assistant'
        && item.finalContent
        && (
          (item.runId && pendingAssistantRunIds.has(item.runId))
          || matchedLegacyPending
        )
      ) {
        return false
      }
      if (item.role === 'system' && item.phase === 'error') {
        if (seenSystemErrors.has(item.content)) return false
        seenSystemErrors.add(item.content)
      }
      return true
    })
    return [
      ...anchorAssistantReplies([...dedupedChatMessages, ...pendingChatMessages]),
      ...(
      shouldShowThinkingMessage
        ? [{
            id: `thinking-${activeSessionId ?? 'none'}-${activeRuntimeRun?.runId ?? 'pending'}`,
            role: 'assistant' as const,
            label: '思考',
            createdAt: 'streaming',
            content: '正在理解需求、检查会话上下文，并准备回复。',
            pending: true,
            phase: 'thinking' as const,
          }]
        : []
      ),
    ]
  }, [activeRuntimeRun, activeSessionId, chatMessages, pendingChatMessages, shouldShowThinkingMessage])
  const chatScrollKey = useMemo(() => visibleChatMessages.map((item) => [
    String(item.id),
    item.content.length,
    item.thinkingContent?.length ?? 0,
    item.finalContent?.length ?? 0,
    item.toolItems?.length ?? 0,
    item.pending ? 'pending' : 'done',
  ].join(':')).join('|'), [visibleChatMessages])
  const mcpCatalog = useMemo(() => mcpCatalogQuery.data?.items ?? [], [mcpCatalogQuery.data?.items])
  const approvals = approvalsQuery.data ?? []
  const savedMcpServerIds = useMemo(() => enabledBindingServerIds(mcpBindingsQuery.data ?? []), [mcpBindingsQuery.data])
  const mcpCatalogNameById = useMemo(() => new Map(mcpCatalog.map((server) => [server.id, server.name])), [mcpCatalog])
  const selectedMcpSummaryItems = useMemo(() =>
    selectedMcpServerIds.map((serverId) => ({
      id: serverId,
      name: mcpCatalogNameById.get(serverId) ?? serverId,
    })),
  [mcpCatalogNameById, selectedMcpServerIds])
  const hasMcpSelectionChanges = stableSelectionKey(selectedMcpServerIds) !== stableSelectionKey(savedMcpServerIds)

  useEffect(() => {
    if (activeTab !== 'chat') return
    const scrollToBottom = () => {
      const container = chatScrollRef.current
      if (container) {
        container.scrollTop = container.scrollHeight
        return
      }
      chatEndRef.current?.scrollIntoView?.({ block: 'end' })
    }
    scrollToBottom()
    const frame = window.requestAnimationFrame(scrollToBottom)
    return () => window.cancelAnimationFrame(frame)
  }, [activeTab, chatScrollKey, activeSessionId])

  useEffect(() => {
    if (!namespace && namespaces.length > 0) {
      setNamespace(namespaces[0].slug)
    }
  }, [namespace, namespaces])

  useEffect(() => {
    if (mode === 'CREATE_SKILL') {
      setSourceNamespace('')
      setSourceSlug('')
      setSourceVersion('')
      if (!targetVersionChoices.includes(targetVersion)) {
        setTargetVersion('0.1.0')
      }
      return
    }
    setSourceNamespace(namespace)
  }, [mode, namespace, targetVersion, targetVersionChoices])

  useEffect(() => {
    if (mode !== 'UPDATE_SKILL') return
    if (sourceSkills.length > 0 && !sourceSkills.some((skill) => skill.slug === sourceSlug)) {
      setSourceSlug(sourceSkills[0].slug)
    }
    if (sourceSkills.length === 0 && sourceSlug) {
      setSourceSlug('')
    }
  }, [mode, sourceSkills, sourceSlug])

  useEffect(() => {
    if (mode !== 'UPDATE_SKILL') return
    if (sourceSlug) {
      setTargetSlug(sourceSlug)
    }
  }, [mode, sourceSlug])

  useEffect(() => {
    if (mode !== 'UPDATE_SKILL') return
    if (sourceVersions.length > 0 && !sourceVersions.some((version) => version.version === sourceVersion)) {
      setSourceVersion(sourceVersions[0].version)
      setTargetVersion(bumpPatchVersion(sourceVersions[0].version))
    }
    if (sourceVersions.length === 0 && sourceVersion) {
      setSourceVersion('')
    }
  }, [mode, sourceVersions, sourceVersion])

  useEffect(() => {
    if (!mcpBindingsQuery.isSuccess) return
    setSelectedMcpServerIds(enabledBindingServerIds(mcpBindingsQuery.data ?? []))
  }, [activeSessionId, mcpBindingsQuery.data, mcpBindingsQuery.isSuccess])

  const invalidateSessionData = async (sessionId: number) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['workbench', 'sessions'] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'session', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'files', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'events', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'diff', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'approvals', sessionId] }),
    ])
  }

  const invalidateMcpData = async (sessionId: number) => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['workbench', 'mcp-bindings', sessionId] }),
      queryClient.invalidateQueries({ queryKey: ['workbench', 'events', sessionId] }),
    ])
  }

  const clearPackagePublishState = () => {
    setPackagePreview(null)
    setPackagePreviewSessionId(null)
    setPublishResult(null)
  }

  const appendPendingThinking = (id: string, content: string) => {
    if (!content) return
    setPendingChatMessages((current) => current.map((item) => {
      if (item.id !== id) return item
      const previous = item.thinkingContent === '正在思考...' ? '' : item.thinkingContent ?? ''
      const nextThinking = `${previous}${content}`
      return { ...item, thinkingContent: nextThinking, content: item.finalContent || nextThinking }
    }))
  }

  const appendPendingFinal = (id: string, content: string) => {
    if (!content) return
    setPendingChatMessages((current) => current.map((item) => {
      if (item.id !== id) return item
      const nextFinal = `${item.finalContent ?? ''}${content}`
      return { ...item, finalContent: nextFinal, content: nextFinal, phase: 'final' }
    }))
  }

  const setPendingRunId = (id: string, runId: string) => {
    if (!runId) return
    setPendingChatMessages((current) => current.map((item) =>
      item.id === id ? { ...item, runId } : item,
    ))
  }

  const appendPendingToolItem = (id: string, toolItem: { id: string; label: string; content: string }) => {
    setPendingChatMessages((current) => current.map((item) => {
      if (item.id !== id) return item
      if (item.toolItems?.some((existing) => existing.id === toolItem.id)) return item
      return { ...item, toolItems: [...(item.toolItems ?? []), toolItem] }
    }))
  }

  const markPendingMessageDone = (id: string) => {
    setPendingChatMessages((current) => current.map((item) =>
      item.id === id ? { ...item, pending: false } : item,
    ))
  }

  const appendStreamRuntimeEvent = (event: WorkbenchRuntimeStreamEvent, assistantId: string, eventId: string) => {
    const chatMessage = runtimeStreamEventToChatMessage(event, eventId)
    if (!chatMessage) return
    appendPendingToolItem(assistantId, {
      id: eventId,
      label: chatMessage.label,
      content: chatMessage.content,
    })
  }

  const createSessionMutation = useMutation({
    mutationFn: () => workbenchApi.createSession(buildCreateRequest({
      namespace,
      mode,
      sourceNamespace,
      sourceSlug,
      sourceVersion,
      targetSlug,
      targetVersion,
      expiresInHours,
    })),
    onSuccess: (created: WorkbenchSession) => {
      setActiveSessionId(created.id)
      setActiveTab('chat')
      setSelectedFile(null)
      setFileContent(null)
      setEditorContent('')
      setFileWarning('')
      setOperationError('')
      setPendingChatMessages([])
      setShowSessionSetup(false)
      clearPackagePublishState()
      queryClient.invalidateQueries({ queryKey: ['workbench', 'sessions'] })
    },
    onError: (error) => {
      setOperationError(displayError(error))
    },
  })

  const importSourceMutation = useMutation({
    mutationFn: (sessionId: number) => workbenchApi.importSource(sessionId),
    onSuccess: async (_result, sessionId) => {
      if (activeSessionId === sessionId) {
        setSelectedFile(null)
        setFileContent(null)
        setEditorContent('')
        setFileWarning('')
        setOperationError('')
        clearPackagePublishState()
      }
      await invalidateSessionData(sessionId)
    },
    onError: (error, sessionId) => {
      if (activeSessionId === sessionId) {
        setOperationError(displayError(error))
      }
    },
  })

  const readFileMutation = useMutation({
    mutationFn: ({ sessionId, file }: { sessionId: number; file: WorkbenchFile }) => workbenchApi.readFile(sessionId, file.path),
    onSuccess: (content, variables) => {
      if (activeSessionId !== variables.sessionId || selectedFile?.path !== variables.file.path) {
        return
      }
      setFileContent(content)
      setEditorContent(content.content)
      setFileWarning('')
      setOperationError('')
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId && selectedFile?.path === variables.file.path) {
        setOperationError(displayError(error))
      }
    },
  })

  const writeFileMutation = useMutation({
    mutationFn: (variables: { sessionId: number; path: string; content: string; contentType: string }) =>
      workbenchApi.writeFile(variables.sessionId, variables.path, {
        content: variables.content,
        contentType: variables.contentType,
      }),
    onSuccess: async (updated, variables) => {
      if (activeSessionId === variables.sessionId && selectedFile?.path === variables.path) {
        setFileContent(updated)
        setEditorContent(updated.content)
        setOperationError('')
        clearPackagePublishState()
      }
      await invalidateSessionData(variables.sessionId)
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId && selectedFile?.path === variables.path) {
        setOperationError(displayError(error))
      }
    },
  })

  const sendMessageMutation = useMutation({
    mutationFn: async (variables: {
      sessionId: number
      message: string
      pendingUserId: string
      thinkingId: string
    }) => {
      let completedRun: WorkbenchRuntimeRunResponse | null = null
      await workbenchApi.sendMessageStream(variables.sessionId, variables.message, (event) => {
        if (event.event === 'run_started') {
          const runId = typeof event.data.runId === 'string' ? event.data.runId : 'stream'
          setPendingRunId(variables.thinkingId, runId)
          setLastRun({ runId, status: 'STARTED', message: '模型流式响应中', eventCount: 0 })
          return
        }
        if (event.event === 'thinking_delta') {
          const content = typeof event.data.content === 'string' ? event.data.content : ''
          appendPendingThinking(variables.thinkingId, content)
          return
        }
        if (event.event === 'message_delta') {
          const content = typeof event.data.content === 'string' ? event.data.content : ''
          appendPendingFinal(variables.thinkingId, content)
          return
        }
        if (event.event === 'runtime_event') {
          appendStreamRuntimeEvent(
            event,
            variables.thinkingId,
            `stream-event-${variables.sessionId}-${Date.now()}-${Math.random().toString(16).slice(2)}`,
          )
          return
        }
        if (event.event === 'completed') {
          completedRun = streamRunResponse(event.data)
          setLastRun(completedRun)
          markPendingMessageDone(variables.thinkingId)
          return
        }
        if (event.event === 'error') {
          const message = typeof event.data.message === 'string' ? event.data.message : '模型调用失败'
          throw new Error(message)
        }
      })
      return completedRun ?? { runId: 'stream', status: 'COMPLETED', message: '模型已响应', eventCount: 0 }
    },
    onMutate: (variables) => {
      if (activeSessionId !== variables.sessionId) return
      setMessage('')
      setPendingChatMessages((current) => [
        ...current,
        {
          id: variables.pendingUserId,
          role: 'user',
          label: '你',
          createdAt: 'sending',
          content: variables.message,
          pending: true,
        },
        {
          id: variables.thinkingId,
          role: 'assistant',
          label: '工作台',
          createdAt: 'streaming',
          content: '正在思考...',
          thinkingContent: '正在思考...',
          finalContent: '',
          toolItems: [],
          pending: true,
          phase: 'thinking',
          sourceUserContent: variables.message,
        },
      ])
      setOperationError('')
      return {
        pendingUserId: variables.pendingUserId,
        thinkingId: variables.thinkingId,
      }
    },
    onSuccess: async (run, variables, context) => {
      if (activeSessionId === variables.sessionId) {
        setLastRun(run)
        setMessage('')
        setOperationError('')
        clearPackagePublishState()
      }
      await invalidateSessionData(variables.sessionId)
      if (activeSessionId === variables.sessionId && context) {
        setPendingChatMessages((current) => current.filter((item) => {
          if (String(item.id) === context.pendingUserId) return false
          if (String(item.id) !== context.thinkingId) return true
          const hasStreamContent = Boolean(
            item.finalContent
            || ((item.thinkingContent ?? '') && item.thinkingContent !== '正在思考...')
            || (item.toolItems?.length ?? 0) > 0,
          )
          return hasStreamContent
        }))
      }
    },
    onError: (error, variables, context) => {
      if (activeSessionId === variables.sessionId) {
        if (context) {
          setPendingChatMessages((current) => current.filter((item) =>
            ![context.pendingUserId, context.thinkingId].includes(String(item.id)),
          ))
        }
        setMessage((current) => current || variables.message)
        setOperationError(displayError(error))
      }
    },
  })

  const cancelRunMutation = useMutation({
    mutationFn: (variables: { sessionId: number; runId: string }) => workbenchApi.cancelRun(variables.sessionId, variables.runId),
    onSuccess: async (run, variables) => {
      if (activeSessionId === variables.sessionId) {
        setLastRun(run)
        setOperationError('')
        clearPackagePublishState()
      }
      await invalidateSessionData(variables.sessionId)
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId && lastRun?.runId === variables.runId) {
        setOperationError(displayError(error))
      }
    },
  })

  const packagePreviewMutation = useMutation({
    mutationFn: (variables: { sessionId: number; visibility: PublishWorkbenchPackageRequest['visibility'] }) =>
      workbenchApi.packagePreview(variables.sessionId, { visibility: variables.visibility }),
    onSuccess: (preview, variables) => {
      if (activeSessionId !== variables.sessionId) return
      setPackagePreview(preview)
      setPackagePreviewSessionId(variables.sessionId)
      setPublishResult(null)
      setOperationError('')
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId) {
        setOperationError(displayError(error))
      }
    },
  })

  const publishPackageMutation = useMutation({
    mutationFn: (variables: { sessionId: number; confirmPackageFingerprint: string; visibility: PublishWorkbenchPackageRequest['visibility'] }) =>
      workbenchApi.publishPackage(variables.sessionId, {
        confirmPackageFingerprint: variables.confirmPackageFingerprint,
        visibility: variables.visibility,
      }),
    onSuccess: async (result, variables) => {
      if (activeSessionId === variables.sessionId) {
        setPublishResult(result)
        setOperationError('')
      }
      await invalidateSessionData(variables.sessionId)
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId) {
        setOperationError(displayError(error))
      }
    },
  })

  const saveMcpBindingsMutation = useMutation({
    mutationFn: (variables: { sessionId: number; serverIds: string[] }) =>
      workbenchApi.saveMcpBindings(variables.sessionId, { serverIds: variables.serverIds }),
    onSuccess: async (bindings, variables) => {
      if (activeSessionId === variables.sessionId) {
        setSelectedMcpServerIds(enabledBindingServerIds(bindings))
        setOperationError('')
      }
      await invalidateMcpData(variables.sessionId)
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId) {
        setOperationError(displayError(error))
      }
    },
  })

  const decideApprovalMutation = useMutation({
    mutationFn: (variables: { sessionId: number; approvalId: number; decision: 'approve' | 'reject' }) =>
      variables.decision === 'approve'
        ? workbenchApi.approveApproval(variables.sessionId, variables.approvalId)
        : workbenchApi.rejectApproval(variables.sessionId, variables.approvalId),
    onSuccess: async (_approval, variables) => {
      if (activeSessionId === variables.sessionId) {
        setOperationError('')
        clearPackagePublishState()
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['workbench', 'approvals', variables.sessionId] }),
        queryClient.invalidateQueries({ queryKey: ['workbench', 'events', variables.sessionId] }),
        queryClient.invalidateQueries({ queryKey: ['workbench', 'session', variables.sessionId] }),
      ])
    },
    onError: (error, variables) => {
      if (activeSessionId === variables.sessionId) {
        setOperationError(displayError(error))
      }
    },
  })

  const loadSessionById = (sessionId: number) => {
    setActiveSessionId(sessionId)
    setActiveTab('chat')
    setSelectedFile(null)
    setFileContent(null)
    setEditorContent('')
    setFileWarning('')
    setLastRun(null)
    setOperationError('')
    setPendingChatMessages([])
    setSelectedMcpServerIds([])
    setShowSessionSetup(false)
    setShowMcpCatalog(false)
    clearPackagePublishState()
  }

  const selectFile = (file: WorkbenchFile) => {
    setSelectedFile(file)
    setActiveTab('editor')
    setFileContent(null)
    setEditorContent('')
    if (!isEditableTextFile(file)) {
      setFileWarning(NON_TEXT_WARNING)
      return
    }
    setFileWarning('')
    if (activeSessionId !== null) {
      readFileMutation.mutate({ sessionId: activeSessionId, file })
    }
  }

  const canCreateSession = namespace.trim() && targetSlug.trim() && targetVersion.trim()
    && (mode === 'CREATE_SKILL' || (sourceNamespace.trim() && sourceSlug.trim() && sourceVersion.trim()))
  const canUseActiveSession = !!activeSession && !sessionQuery.isError
  const canEditDraftSession = canUseActiveSession && activeSession.status === 'DRAFT'
  const canEditWorkspace = canUseActiveSession && ['DRAFT', 'RUNNING'].includes(activeSession.status)
  const canSendMessage = canEditWorkspace && !activeRuntimeRun && !sendMessageMutation.isPending && message.trim().length > 0
  const canAdjustMcpBindings = canEditWorkspace && !activeRuntimeRun
  const canSaveMcpBindings = canAdjustMcpBindings && hasMcpSelectionChanges && !saveMcpBindingsMutation.isPending
  const mcpSaveHint = !canUseActiveSession
    ? '先创建或载入会话后再预选 MCP 服务。'
    : !canEditWorkspace
      ? '当前会话状态不可继续调整 MCP 预选。'
      : activeRuntimeRun
        ? '模型正在回复，完成或取消后可继续调整 MCP 预选。'
      : saveMcpBindingsMutation.isPending
        ? '正在保存 MCP 预选。'
        : hasMcpSelectionChanges
          ? '有未保存的 MCP 预选变更，将从下一轮对话开始生效。'
          : savedMcpServerIds.length > 0
            ? 'MCP 预选已保存，将用于后续对话。'
            : '未预选 MCP 服务。'
  const activePackagePreview = packagePreviewSessionId === activeSession?.id ? packagePreview : null
  const canRequestPackagePreview = canEditWorkspace && !packagePreviewMutation.isPending && !publishPackageMutation.isPending
  const canPublishPackage = canUseActiveSession
    && canEditWorkspace
    && !!activePackagePreview?.readyToPublish
    && !!activePackagePreview?.packageFingerprint
    && !packagePreviewMutation.isPending
    && !publishPackageMutation.isPending
  const sessionLabel = useMemo(() => {
    if (!activeSession) return '尚未载入会话'
    return `${sessionTitle(activeSession)} · ${sessionActivityLabel(activeSession.status, activeRuntimeRun)}`
  }, [activeRuntimeRun, activeSession])
  const shouldShowSessionSetup = showSessionSetup

  const toggleMcpServer = (serverId: string) => {
    setSelectedMcpServerIds((current) => current.includes(serverId)
      ? current.filter((id) => id !== serverId)
      : [...current, serverId])
  }

  const sendCurrentMessage = () => {
    if (!activeSession || !canSendMessage) return
    const now = Date.now()
    sendMessageMutation.mutate({
      sessionId: activeSession.id,
      message: message.trim(),
      pendingUserId: `pending-user-${activeSession.id}-${now}`,
      thinkingId: `stream-thinking-${activeSession.id}-${now}`,
    })
  }

  return (
    <div className="animate-fade-up bg-[#f8fafc] text-foreground">
      {(operationError || sessionQuery.isError) && (
        <div className="border-b border-red-200 bg-red-50 px-6 py-3 text-sm text-red-700" role="alert">
          {operationError || `会话读取失败：${displayError(sessionQuery.error)}`}
        </div>
      )}

      <div className="grid min-h-[calc(100vh-68px)] grid-cols-1 bg-[#f8fafc] xl:h-[calc(100vh-68px)] xl:min-h-0 xl:grid-cols-[300px_minmax(720px,1fr)_320px] xl:overflow-hidden">
        {activeSession && showSessionSetup && (
          <button
            type="button"
            className="fixed inset-x-0 bottom-0 top-[68px] z-30 bg-slate-950/25 backdrop-blur-[1px] xl:hidden"
            aria-label="关闭会话面板"
            onClick={() => setShowSessionSetup(false)}
          />
        )}
        <aside className={cn(
          'min-h-0 flex-col border-r border-border/70 bg-background',
          activeSession
            ? showSessionSetup
              ? 'fixed left-0 top-[68px] z-40 flex h-[calc(100vh-68px)] w-[320px] max-w-[86vw] shadow-2xl xl:static xl:order-1 xl:flex xl:h-auto xl:w-auto xl:max-w-none xl:shadow-none'
              : 'hidden xl:order-1 xl:flex'
            : 'order-2 flex xl:order-1 xl:flex',
        )}>
          <div className="border-b border-border/70 px-5 py-4">
            <div className="flex items-center justify-between gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary">
                <MessageSquare className="h-5 w-5" aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <h1 className="truncate text-lg font-semibold">工作台</h1>
                <p className="truncate text-xs text-muted-foreground">先对话，再生成、更新并发布 Skill 文件</p>
              </div>
              <Button
                type="button"
                size="sm"
                variant={shouldShowSessionSetup ? 'secondary' : 'outline'}
                onClick={() => setShowSessionSetup((current) => !current)}
              >
                {shouldShowSessionSetup ? '收起' : '新建'}
              </Button>
            </div>
          </div>

          <div className="min-h-0 flex-1 overflow-auto px-4 py-4">
            {!shouldShowSessionSetup && (
              <section className="mb-4 rounded-lg border border-dashed border-border/80 bg-[#fbfcfd] p-4 text-sm">
                <p className="font-semibold">选择一个会话继续对话</p>
                <p className="mt-1 text-muted-foreground">
                  新建会话只用于确定工作目录和目标范围，后续输入都是普通对话消息。
                </p>
                <Button
                  type="button"
                  size="sm"
                  className="mt-3 w-full"
                  onClick={() => setShowSessionSetup(true)}
                >
                  新建工作会话
                </Button>
              </section>
            )}
            {shouldShowSessionSetup && (
            <section className="mb-4 rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <h2 className="text-sm font-semibold">会话目标设置</h2>
                <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                  {mode === 'CREATE_SKILL' ? '创建' : '更新'}
                </span>
              </div>

              <div className="space-y-4">
                <div className="space-y-2">
                  <Label htmlFor="workbench-mode">会话模式</Label>
                  <Select value={mode} onValueChange={(value) => setMode(value as WorkbenchMode)}>
                    <SelectTrigger id="workbench-mode" aria-label="会话模式">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="CREATE_SKILL">创建技能</SelectItem>
                      <SelectItem value="UPDATE_SKILL">更新技能</SelectItem>
                    </SelectContent>
                  </Select>
                </div>

                <div className="space-y-2">
                  <Label htmlFor="workbench-namespace">命名空间</Label>
                  <Select value={namespace} onValueChange={setNamespace} disabled={namespacesQuery.isLoading || namespaces.length === 0}>
                    <SelectTrigger id="workbench-namespace" aria-label="命名空间">
                      <SelectValue placeholder={namespacesQuery.isLoading ? '正在读取命名空间' : '选择命名空间'} />
                    </SelectTrigger>
                    <SelectContent>
                      {namespaces.map((item) => (
                        <SelectItem key={item.slug} value={item.slug}>{namespaceLabel(item)}</SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {mode === 'UPDATE_SKILL' && (
                  <>
                    <div className="space-y-2">
                      <Label htmlFor="workbench-source-namespace">源命名空间</Label>
                      <Select value={namespace} onValueChange={setNamespace} disabled={namespacesQuery.isLoading || namespaces.length === 0}>
                        <SelectTrigger id="workbench-source-namespace" aria-label="源命名空间">
                          <SelectValue placeholder={namespacesQuery.isLoading ? '正在读取命名空间' : '选择命名空间'} />
                        </SelectTrigger>
                        <SelectContent>
                          {namespaces.map((item) => (
                            <SelectItem key={item.slug} value={item.slug}>{namespaceLabel(item)}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="workbench-source-slug">源技能</Label>
                      <Select value={sourceSlug} onValueChange={setSourceSlug} disabled={!namespace || sourceSkillsQuery.isLoading || sourceSkills.length === 0}>
                        <SelectTrigger id="workbench-source-slug" aria-label="源技能">
                          <SelectValue placeholder={sourceSkillsQuery.isLoading ? '正在读取技能' : '选择技能'} />
                        </SelectTrigger>
                        <SelectContent>
                          {sourceSkills.map((skill) => (
                            <SelectItem key={skill.slug} value={skill.slug}>{skillLabel(skill)}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                    <div className="space-y-2">
                      <Label htmlFor="workbench-source-version">源版本</Label>
                      <Select value={sourceVersion} onValueChange={(value) => {
                        setSourceVersion(value)
                        setTargetVersion(bumpPatchVersion(value))
                      }} disabled={!sourceSlug || sourceVersionsQuery.isLoading || sourceVersions.length === 0}>
                        <SelectTrigger id="workbench-source-version" aria-label="源版本">
                          <SelectValue placeholder={sourceVersionsQuery.isLoading ? '正在读取版本' : '选择版本'} />
                        </SelectTrigger>
                        <SelectContent>
                          {sourceVersions.map((version: SkillVersion) => (
                            <SelectItem key={version.version} value={version.version}>{version.version} · {sessionStatusLabel(version.status)}</SelectItem>
                          ))}
                        </SelectContent>
                      </Select>
                    </div>
                  </>
                )}

                <div className="space-y-2">
                  <Label htmlFor="workbench-target-slug">目标标识</Label>
                  {mode === 'CREATE_SKILL' ? (
                    <Input id="workbench-target-slug" value={targetSlug} onChange={(event) => setTargetSlug(event.target.value)} placeholder="my-skill" />
                  ) : (
                    <Select value={sourceSlug} onValueChange={setSourceSlug} disabled={!namespace || sourceSkillsQuery.isLoading || sourceSkills.length === 0}>
                      <SelectTrigger id="workbench-target-slug" aria-label="技能">
                        <SelectValue placeholder={sourceSkillsQuery.isLoading ? '正在读取技能' : '选择技能'} />
                      </SelectTrigger>
                      <SelectContent>
                        {sourceSkills.map((skill) => (
                          <SelectItem key={skill.slug} value={skill.slug}>{skillLabel(skill)}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  )}
                </div>

                <div className="grid grid-cols-[minmax(0,1fr)_104px] gap-3">
                  <div className="space-y-2">
                    <Label htmlFor="workbench-target-version">目标版本</Label>
                    <Select value={targetVersion} onValueChange={setTargetVersion}>
                      <SelectTrigger id="workbench-target-version" aria-label="目标版本">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {targetVersionChoices.map((version) => (
                          <SelectItem key={version} value={version}>{version}</SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="workbench-expires">有效小时</Label>
                    <Input id="workbench-expires" value={expiresInHours} onChange={(event) => setExpiresInHours(event.target.value)} inputMode="numeric" />
                  </div>
                </div>

                <Button
                  type="button"
                  className="w-full gap-2"
                  disabled={!canCreateSession || createSessionMutation.isPending}
                  onClick={() => createSessionMutation.mutate()}
                >
                  {createSessionMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Play className="h-4 w-4" aria-hidden="true" />}
                  开始工作会话
                </Button>
              </div>
            </section>
            )}

            <section>
              <div className="mb-3 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <History className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  <h2 className="text-sm font-semibold">会话历史</h2>
                </div>
                {sessionsQuery.isFetching && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" aria-hidden="true" />}
              </div>
              <div className="relative mb-3">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
                <Input
                  aria-label="搜索会话历史"
                  value={sessionSearch}
                  onChange={(event) => setSessionSearch(event.target.value)}
                  placeholder="搜索目标、状态或版本"
                  className="h-9 pl-9"
                />
              </div>
              <div className="space-y-2">
                {sessionsQuery.isError && (
                  <p role="alert" className="rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-600">会话历史读取失败：{displayError(sessionsQuery.error)}</p>
                )}
                {!sessionsQuery.isLoading && !sessionsQuery.isError && recentSessions.length === 0 && (
                  <p className="rounded-lg border border-dashed border-border/80 px-3 py-4 text-sm text-muted-foreground">暂无会话，创建后会显示在这里。</p>
                )}
                {!sessionsQuery.isLoading && !sessionsQuery.isError && recentSessions.length > 0 && filteredRecentSessions.length === 0 && (
                  <p className="rounded-lg border border-dashed border-border/80 px-3 py-4 text-sm text-muted-foreground">没有匹配的会话。</p>
                )}
                {filteredRecentSessions.map((session) => {
                  const isActiveSession = activeSessionId === session.id
                  const visibleRun = isActiveSession ? activeRuntimeRun : session.activeRun
                  return (
                    <button
                      key={session.id}
                      type="button"
                      className={cn(
                        'flex w-full items-start gap-3 rounded-lg border px-3 py-3 text-left text-sm transition-colors',
                        isActiveSession
                          ? 'border-primary/40 bg-primary/10 text-foreground shadow-sm'
                          : 'border-transparent bg-transparent hover:border-border/80 hover:bg-background',
                      )}
                      onClick={() => loadSessionById(session.id)}
                    >
                      <span className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-secondary text-xs font-semibold text-muted-foreground">
                        {sessionAvatarText(session)}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="flex items-center justify-between gap-2">
                          <span className="truncate font-semibold">{sessionTitle(session)}</span>
                          <span className={cn(
                            'shrink-0 rounded-full border px-2 py-0.5 text-xs font-medium',
                            sessionActivityClassName(session.status, visibleRun),
                          )}>
                            {sessionActivityLabel(session.status, visibleRun)}
                          </span>
                        </span>
                        <span className="mt-1 block truncate text-xs text-muted-foreground">
                          会话 #{session.id} · {sessionSubtitle(session)}
                        </span>
                      </span>
                    </button>
                  )
                })}
                {!sessionsQuery.isLoading && !sessionsQuery.isError && recentSessions.length >= sessionLimit && (
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="w-full"
                    onClick={() => setSessionLimit((current) => current + 20)}
                  >
                    加载更多会话
                  </Button>
                )}
              </div>
            </section>
          </div>
        </aside>

        <main className="order-1 flex min-h-[calc(100vh-68px)] flex-col bg-background xl:order-2 xl:min-h-0">
          <div className="flex min-h-0 flex-1 flex-col">
            <Tabs value={activeTab} onValueChange={setActiveTab} className="flex min-h-0 flex-1 flex-col">
              <div className="flex flex-col gap-4 border-b border-border/70 px-6 py-4 xl:flex-row xl:items-center xl:justify-between">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="truncate text-sm font-semibold text-foreground">{sessionLabel}</p>
                    {activeSession && (
                      <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                        {workbenchModeLabel(activeSession.mode)} · {activeSession.targetVersion}
                      </span>
                    )}
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {activeSession
                      ? `${activeSession.targetSlug}@${activeSession.targetVersion} 已作为模型上下文注入 · ${activeSession.fileCount} 个文件 · 后续输入是普通对话，不会自动发布`
                      : '从左侧创建或载入会话后开始编辑。'}
                  </p>
                </div>
                <div className="flex flex-wrap items-center gap-3">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="xl:hidden"
                    onClick={() => setShowSessionSetup((current) => !current)}
                  >
                    {showSessionSetup ? '收起会话' : '会话'}
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="gap-2"
                    disabled={!canEditDraftSession || activeSession.mode !== 'UPDATE_SKILL' || importSourceMutation.isPending}
                    onClick={() => activeSession && importSourceMutation.mutate(activeSession.id)}
                  >
                    <UploadCloud className="h-4 w-4" aria-hidden="true" />
                    导入源版本
                  </Button>
                  <TabsList>
                    <TabsTrigger value="chat"><MessageSquare className="mr-2 h-4 w-4" aria-hidden="true" />对话</TabsTrigger>
                    <TabsTrigger value="editor">编辑器</TabsTrigger>
                    <TabsTrigger value="diff"><GitCompare className="mr-2 h-4 w-4" aria-hidden="true" />差异</TabsTrigger>
                    <TabsTrigger value="review">发布</TabsTrigger>
                    <TabsTrigger value="tools" className="xl:hidden">工具</TabsTrigger>
                  </TabsList>
                </div>
              </div>

              <div ref={chatScrollRef} className="min-h-0 flex-1 overflow-auto px-5 py-5 lg:px-8">
                <div className="w-full">

            <TabsContent value="chat" className="m-0">
              <div className="flex min-h-[420px] w-full flex-col">
                {!activeSession && (
                  <div className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-border/80 px-6 py-12 text-center">
                    <div className="max-w-sm space-y-3">
                      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-primary/10 text-primary">
                        <MessageSquare className="h-5 w-5" aria-hidden="true" />
                      </div>
                      <div>
                        <h2 className="text-base font-semibold">先创建或载入一个工作会话</h2>
                        <p className="mt-1 text-sm text-muted-foreground">
                          会话只是工作空间和目标范围。后续每条输入都是普通对话指令，不会自动发布版本。
                        </p>
                      </div>
                    </div>
                  </div>
                )}
                {activeSession && visibleChatMessages.length === 0 && (
                  <div className="flex flex-1 items-center justify-center rounded-lg border border-dashed border-border/80 px-6 py-12 text-center">
                    <div className="max-w-md space-y-3">
                      <div className="mx-auto flex h-11 w-11 items-center justify-center rounded-full bg-primary/10 text-primary">
                        <MessageSquare className="h-5 w-5" aria-hidden="true" />
                      </div>
                      <div>
                        <h2 className="text-base font-semibold">描述你想让工作台完成的事</h2>
                        <p className="mt-1 text-sm text-muted-foreground">
                          可以先讨论目标、补充约束、要求生成或修改文件。发布新版本仍需要到“发布”页签中确认。
                        </p>
                      </div>
                    </div>
                  </div>
                )}
                {activeSession && visibleChatMessages.length > 0 && (
                  <div className="flex w-full flex-1 flex-col gap-5 px-1 sm:px-3 lg:px-6">
                    {visibleChatMessages.map((chatMessage) => (
                      <div
                        key={chatMessage.id}
                        className={cn(
                          'flex w-full gap-3',
                          chatMessage.role === 'user' ? 'justify-end' : 'justify-start',
                        )}
                      >
                        {chatMessage.role !== 'user' && (
                          <div className={cn(
                            'mt-1 flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
                            chatMessage.phase === 'tool' ? 'bg-blue-50 text-blue-600' : 'bg-primary/10 text-primary',
                          )}>
                            {chatMessage.phase === 'tool'
                              ? <Cable className="h-4 w-4" aria-hidden="true" />
                              : <MessageSquare className="h-4 w-4" aria-hidden="true" />}
                          </div>
                        )}
                        <div
                          className={cn(
                            'rounded-2xl px-4 py-3 text-sm shadow-sm',
                            chatMessage.role === 'user'
                              ? 'max-w-[min(62%,560px)] rounded-br-md bg-primary text-primary-foreground break-words whitespace-pre-wrap'
                              : chatMessage.role === 'system'
                                ? 'max-w-[min(86%,940px)] rounded-bl-md border border-amber-200 bg-amber-50 text-amber-900'
                                : chatMessage.phase === 'tool'
                                  ? 'max-w-[min(86%,940px)] rounded-bl-md border border-blue-100 bg-blue-50/70 text-blue-950'
                                  : 'max-w-[min(86%,940px)] rounded-bl-md border border-border/70 bg-background text-foreground',
                          )}
                        >
                          <div className="mb-1 flex items-center gap-2 text-xs opacity-75">
                            <span className="font-medium">{chatMessage.label}</span>
                            {formatChatTime(chatMessage.createdAt) && (
                              <span title={chatMessage.createdAt}>{formatChatTime(chatMessage.createdAt)}</span>
                            )}
                            {chatMessage.pending && chatMessage.role !== 'user' && (
                              <span
                                className="inline-flex h-5 w-5 items-center justify-center rounded-full bg-background/80"
                                aria-label="正在生成回复"
                              >
                                <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />
                              </span>
                            )}
                          </div>
                          {chatMessage.role === 'assistant' && (chatMessage.thinkingContent || (chatMessage.toolItems?.length ?? 0) > 0) && (
                            <div className="mb-3 space-y-2">
                              {chatMessage.thinkingContent && (
                                <details
                                  className="group rounded-lg border border-border/60 bg-secondary/20 px-3 py-2"
                                  open={chatMessage.pending && !chatMessage.finalContent}
                                >
                                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 text-xs font-medium text-muted-foreground transition-colors hover:text-foreground">
                                    <span className="inline-flex min-w-0 items-center gap-2">
                                      <ChevronRight className="h-3.5 w-3.5 shrink-0 transition-transform group-open:rotate-90" aria-hidden="true" />
                                      {chatMessage.pending && !chatMessage.finalContent && <Loader2 className="h-3 w-3 animate-spin" aria-hidden="true" />}
                                      <span>思考过程</span>
                                    </span>
                                    <span className="shrink-0 text-[11px] font-normal text-muted-foreground/80">
                                      {chatMessage.pending && !chatMessage.finalContent ? '正在思考' : '已折叠'}
                                    </span>
                                  </summary>
                                  <p className="mt-2 max-h-52 overflow-auto whitespace-pre-wrap break-words border-t border-border/60 pt-2 text-xs leading-5 text-muted-foreground">
                                    {chatMessage.thinkingContent}
                                  </p>
                                </details>
                              )}
                              {(chatMessage.toolItems?.length ?? 0) > 0 && (
                                <details
                                  className="group rounded-lg border border-blue-100 bg-blue-50/60 px-3 py-2"
                                  open={chatMessage.pending && !chatMessage.finalContent}
                                >
                                  <summary className="flex cursor-pointer list-none items-center justify-between gap-3 text-xs font-medium text-blue-700 transition-colors hover:text-blue-800">
                                    <span className="inline-flex min-w-0 items-center gap-2">
                                      <ChevronRight className="h-3.5 w-3.5 shrink-0 transition-transform group-open:rotate-90" aria-hidden="true" />
                                      <Cable className="h-3 w-3" aria-hidden="true" />
                                      <span>工具调用（{chatMessage.toolItems?.length ?? 0}）</span>
                                    </span>
                                    <span className="shrink-0 text-[11px] font-normal text-blue-700/70">
                                      {chatMessage.pending && !chatMessage.finalContent ? '执行中' : '已折叠'}
                                    </span>
                                  </summary>
                                  <div className="mt-2 space-y-2">
                                    {chatMessage.toolItems?.map((toolItem) => (
                                      <div key={toolItem.id} className="rounded-md bg-background/80 px-2 py-2 text-xs text-blue-950">
                                        <p className="font-medium">{toolItem.label}</p>
                                        <p className="mt-1 whitespace-pre-wrap break-words leading-5">{toolItem.content}</p>
                                      </div>
                                    ))}
                                  </div>
                                </details>
                              )}
                            </div>
                          )}
                          {(chatMessage.role !== 'assistant' || chatMessage.finalContent || (!chatMessage.thinkingContent && !chatMessage.toolItems?.length)) && (
                            chatMessage.role === 'assistant' ? (
                              <MarkdownRenderer
                                content={chatMessage.finalContent || chatMessage.content}
                                className={cn(
                                  '[&_h1:first-child]:mt-0 [&_h2:first-child]:mt-0 [&_h3:first-child]:mt-0',
                                  '[&_h1]:mb-3 [&_h1]:pb-2 [&_h1]:text-xl [&_h2]:mt-5 [&_h2]:mb-3 [&_h2]:pb-2 [&_h2]:text-lg [&_h3]:mt-4 [&_h3]:text-base',
                                  '[&_p:first-child]:mt-0 [&_p:last-child]:mb-0 [&_p]:my-2 [&_p]:text-sm [&_p]:leading-6',
                                  '[&_ul]:my-3 [&_ol]:my-3 [&_li]:leading-6 [&_table]:text-xs',
                                )}
                              />
                            ) : (
                              <p className="whitespace-pre-wrap break-words leading-6">
                                {chatMessage.finalContent || chatMessage.content}
                              </p>
                            )
                          )}
                          {chatMessage.role === 'assistant' && chatMessage.pending && activeRuntimeRun?.status === 'STARTED' && (
                            <div className="mt-3">
                              <Button
                                type="button"
                                size="sm"
                                variant="outline"
                                disabled={cancelRunMutation.isPending}
                                onClick={() => activeSession && cancelRunMutation.mutate({ sessionId: activeSession.id, runId: activeRuntimeRun.runId })}
                              >
                                取消生成
                              </Button>
                            </div>
                          )}
                        </div>
                      </div>
                    ))}
                    <div ref={chatEndRef} />
                  </div>
                )}
              </div>
            </TabsContent>

            <TabsContent value="editor">
              {!selectedFile && !fileWarning && (
                <div className="flex min-h-[420px] items-center justify-center rounded-lg border border-dashed border-border/80 text-sm text-muted-foreground">
                  从文件列表选择一个 UTF-8 文本文件；移动端可在“工具”页签中选择。
                </div>
              )}
              {fileWarning && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
                  {fileWarning}
                </div>
              )}
              {readFileMutation.isPending && (
                <div className="flex min-h-[420px] items-center justify-center gap-2 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                  正在打开文件...
                </div>
              )}
              {fileContent && !readFileMutation.isPending && (
                <div className="space-y-4">
                  <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0">
                      <h2 className="truncate text-base font-semibold">{fileContent.path}</h2>
                      <p className="text-xs text-muted-foreground">
                        {fileContent.contentType ?? DEFAULT_CONTENT_TYPE} · {formatBytes(fileContent.sizeBytes)}
                      </p>
                    </div>
                    <Button
                      type="button"
                      className="gap-2"
                      disabled={writeFileMutation.isPending || !canEditWorkspace || fileContent === null}
                      onClick={() => {
                        if (activeSessionId === null || fileContent === null) return
                        writeFileMutation.mutate({
                          sessionId: activeSessionId,
                          path: fileContent.path,
                          content: editorContent,
                          contentType: fileContent.contentType ?? selectedFile?.contentType ?? DEFAULT_CONTENT_TYPE,
                        })
                      }}
                    >
                      {writeFileMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <Save className="h-4 w-4" aria-hidden="true" />}
                      保存文件
                    </Button>
                  </div>
                  <Textarea
                  aria-label="文件内容"
                  value={editorContent}
                  onChange={(event) => setEditorContent(event.target.value)}
                  disabled={!canEditWorkspace}
                  spellCheck={false}
                  className="min-h-[520px] resize-y font-mono text-[13px] leading-6"
                  />
                </div>
              )}
            </TabsContent>

            <TabsContent value="diff">
              <div className="space-y-2">
                {diffQuery.isLoading && <p className="text-sm text-muted-foreground">正在计算差异...</p>}
                {diffQuery.isError && (
                  <p role="alert" className="text-sm text-red-600">差异读取失败：{displayError(diffQuery.error)}</p>
                )}
                {(diffQuery.data?.files ?? []).length === 0 && !diffQuery.isLoading && !diffQuery.isError && (
                  <p className="text-sm text-muted-foreground">暂无文件变更。</p>
                )}
                {(diffQuery.data?.files ?? []).map((file) => (
                  <div key={file.path} className="flex items-center justify-between gap-3 rounded-lg border border-border/70 px-3 py-2 text-sm">
                    <span className="min-w-0 truncate font-medium">{file.path}</span>
                    <span className={cn('shrink-0 rounded-full border px-2 py-0.5 text-xs font-semibold', statusClassName(file.status))}>
                      {diffStatusLabel(file.status)}
                    </span>
                  </div>
                ))}
              </div>
            </TabsContent>

            <TabsContent value="review">
              <div className="space-y-5 rounded-lg border border-border/80 p-5">
                <div className="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h2 className="text-base font-semibold">技能包发布</h2>
                    <p className="mt-1 text-sm text-muted-foreground">
                      先生成技能包预览，确认指纹、文件清单和校验结果后，再按选定可见性发布。
                    </p>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2"
                    disabled={!canRequestPackagePreview}
                    onClick={() => activeSession && packagePreviewMutation.mutate({
                      sessionId: activeSession.id,
                      visibility: packageVisibility,
                    })}
                  >
                    {packagePreviewMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <PackageOpen className="h-4 w-4" aria-hidden="true" />}
                    生成预览
                  </Button>
                </div>

                <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_260px]">
                  <div className="space-y-3">
                    {!activePackagePreview && !packagePreviewMutation.isPending && (
                      <div className="rounded-lg border border-dashed border-border/80 px-4 py-6 text-sm text-muted-foreground">
                        尚未生成技能包预览。
                      </div>
                    )}
                    {packagePreviewMutation.isPending && (
                      <div className="flex min-h-24 items-center justify-center gap-2 rounded-lg border border-dashed border-border/80 text-sm text-muted-foreground">
                        <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                        正在生成技能包预览...
                      </div>
                    )}
                    {activePackagePreview && (
                      <div className="space-y-4">
                        <div className="grid gap-3 rounded-lg border border-border/70 bg-background p-3 text-sm md:grid-cols-2">
                          <div className="min-w-0">
                            <p className="text-xs font-medium text-muted-foreground">包指纹</p>
                            <p className="mt-1 break-all font-mono text-xs">{activePackagePreview.packageFingerprint || '缺失'}</p>
                          </div>
                          <div>
                            <p className="text-xs font-medium text-muted-foreground">发布状态</p>
                            <p className="mt-1 font-semibold">{activePackagePreview.readyToPublish ? '可发布' : '不可发布'}</p>
                          </div>
                          <div className="md:col-span-2">
                            <p className="text-xs font-medium text-muted-foreground">校验结果</p>
                            <div className="mt-2 flex flex-wrap items-center gap-2">
                              <span className={cn('rounded-full border px-2 py-0.5 text-xs font-semibold', validationStatusClassName(activePackagePreview.validation.status))}>
                                {activePackagePreview.validation.status}
                              </span>
                              {activePackagePreview.validation.messages.length === 0 && (
                                <span className="text-xs text-muted-foreground">暂无校验消息。</span>
                              )}
                            </div>
                            {activePackagePreview.validation.messages.length > 0 && (
                              <ul className="mt-2 space-y-1 text-sm text-muted-foreground">
                                {activePackagePreview.validation.messages.map((message) => (
                                  <li key={message}>{message}</li>
                                ))}
                              </ul>
                            )}
                          </div>
                        </div>

                        <div className="grid gap-4 xl:grid-cols-2">
                          <div>
                            <h3 className="text-sm font-semibold">包含文件</h3>
                            <div className="mt-2 max-h-64 space-y-2 overflow-auto pr-1">
                              {activePackagePreview.includedFiles.length === 0 && (
                                <p className="rounded-lg border border-border/70 px-3 py-2 text-sm text-muted-foreground">暂无包含文件。</p>
                              )}
                              {activePackagePreview.includedFiles.map((file) => (
                                <div key={file.path} className="rounded-lg border border-border/70 px-3 py-2 text-sm">
                                  <div className="flex items-center justify-between gap-3">
                                    <span className="min-w-0 truncate font-medium">{file.path}</span>
                                    <span className="shrink-0 text-xs text-muted-foreground">{formatBytes(file.sizeBytes)}</span>
                                  </div>
                                  {file.sha256 && <p className="mt-1 break-all font-mono text-xs text-muted-foreground">{file.sha256}</p>}
                                </div>
                              ))}
                            </div>
                          </div>

                          <div>
                            <h3 className="text-sm font-semibold">排除文件</h3>
                            <div className="mt-2 max-h-64 space-y-2 overflow-auto pr-1">
                              {activePackagePreview.excludedFiles.length === 0 && (
                                <p className="rounded-lg border border-border/70 px-3 py-2 text-sm text-muted-foreground">暂无排除文件。</p>
                              )}
                              {activePackagePreview.excludedFiles.map((file) => (
                                <div key={file.path} className="rounded-lg border border-border/70 px-3 py-2 text-sm">
                                  <p className="truncate font-medium">{file.path}</p>
                                  <p className="mt-1 text-xs text-muted-foreground">{file.reason}</p>
                                </div>
                              ))}
                            </div>
                          </div>
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="space-y-4 rounded-lg border border-border/70 bg-secondary/20 p-4">
                    <div className="space-y-2">
                      <Label htmlFor="workbench-package-visibility">可见性</Label>
                      <Select value={packageVisibility} onValueChange={(value) => {
                        setPackageVisibility(value as PublishWorkbenchPackageRequest['visibility'])
                        clearPackagePublishState()
                      }}>
                        <SelectTrigger id="workbench-package-visibility" aria-label="技能包可见性">
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="PRIVATE">{visibilityLabel('PRIVATE')}</SelectItem>
                          <SelectItem value="NAMESPACE_ONLY">{visibilityLabel('NAMESPACE_ONLY')}</SelectItem>
                          <SelectItem value="PUBLIC">{visibilityLabel('PUBLIC')}</SelectItem>
                        </SelectContent>
                      </Select>
                    </div>
                    <Button
                      type="button"
                      className="w-full gap-2"
                      disabled={!canPublishPackage}
                      onClick={() => {
                        if (!activeSession || !activePackagePreview?.packageFingerprint) return
                        publishPackageMutation.mutate({
                          sessionId: activeSession.id,
                          confirmPackageFingerprint: activePackagePreview.packageFingerprint,
                          visibility: packageVisibility,
                        })
                      }}
                    >
                      {publishPackageMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" /> : <UploadCloud className="h-4 w-4" aria-hidden="true" />}
                      发布技能包
                    </Button>

                    {publishResult && (
                      <div className="space-y-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-800">
                        <p className="font-semibold">技能已发布</p>
                        <dl className="grid grid-cols-[auto_minmax(0,1fr)] gap-x-3 gap-y-1">
                          <dt className="text-emerald-700/80">命名空间</dt>
                          <dd className="min-w-0 truncate font-medium">{publishResult.namespace}</dd>
                          <dt className="text-emerald-700/80">技能标识</dt>
                          <dd className="min-w-0 truncate font-medium">{publishResult.slug}</dd>
                          <dt className="text-emerald-700/80">版本</dt>
                          <dd className="font-medium">{publishResult.version}</dd>
                          <dt className="text-emerald-700/80">状态</dt>
                          <dd className="font-medium">{publishResult.status}</dd>
                        </dl>
                      </div>
                    )}
                  </div>
                </div>
              </div>
                  </TabsContent>

                  <TabsContent value="tools" className="m-0 xl:hidden">
                    <div className="space-y-4">
                      <section className="rounded-lg border border-border/80 bg-card p-4 shadow-sm">
                        <div className="mb-3 flex items-center gap-2">
                          <MessageSquare className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                          <h2 className="text-base font-semibold text-foreground">模型与运行时</h2>
                        </div>
                        <div className={cn('rounded-lg border px-3 py-2 text-sm', runtimeConfigTone(runtimeConfig))}>
                          <p className="font-semibold">{runtimeConfigTitle(runtimeConfig)}</p>
                          <p className="mt-1 text-xs leading-5">
                            {runtimeConfigQuery.isError ? '模型配置读取失败，请检查服务配置。' : runtimeConfig?.message ?? '正在读取模型配置状态。'}
                          </p>
                        </div>
                        <dl className="mt-3 grid grid-cols-2 gap-3 text-xs">
                          <div>
                            <dt className="text-muted-foreground">执行器</dt>
                            <dd className="mt-1 font-medium text-foreground">{runtimeConfig?.modelExecutorEnabled ? '已启用' : '未启用'}</dd>
                          </div>
                          <div>
                            <dt className="text-muted-foreground">模型地址</dt>
                            <dd className="mt-1 font-medium text-foreground">{runtimeConfig?.modelBaseUrlConfigured ? '已配置' : '未配置'}</dd>
                          </div>
                        </dl>
                      </section>

                      <section className="rounded-lg border border-border/80 bg-card p-4 shadow-sm">
                        <div className="mb-3 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-2">
                            <FileText className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                            <h2 className="text-base font-semibold">文件</h2>
                          </div>
                          <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">{files.length}</span>
                        </div>
                        <div className="space-y-2">
                          {filesQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取文件列表...</p>}
                          {filesQuery.isError && (
                            <p role="alert" className="text-sm text-red-600">文件列表读取失败：{displayError(filesQuery.error)}</p>
                          )}
                          {!filesQuery.isLoading && !filesQuery.isError && files.length === 0 && (
                            <p className="text-sm text-muted-foreground">暂无文件。更新技能会话可先导入源版本。</p>
                          )}
                          {files.map((file) => (
                            <button
                              key={file.path}
                              type="button"
                              className={cn(
                                'flex w-full items-start justify-between gap-3 rounded-lg border px-3 py-2 text-left text-sm transition-colors',
                                selectedFile?.path === file.path
                                  ? 'border-primary/50 bg-primary/5 text-foreground'
                                  : 'border-border/70 bg-background hover:border-primary/30',
                              )}
                              onClick={() => selectFile(file)}
                            >
                              <span className="min-w-0">
                                <span className="block truncate font-medium">{file.path}</span>
                                <span className="block truncate text-xs text-muted-foreground">{file.contentType ?? 'unknown'}</span>
                              </span>
                              <span className="shrink-0 text-xs text-muted-foreground">{formatBytes(file.sizeBytes)}</span>
                            </button>
                          ))}
                        </div>
                      </section>

                      <section className="rounded-lg border border-border/80 bg-card p-4 shadow-sm">
                        <div className="mb-3 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-2">
                            <Cable className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                            <h2 className="text-base font-semibold">MCP 服务</h2>
                          </div>
                          <Button
                            type="button"
                            size="sm"
                            variant="outline"
                            disabled={!canUseActiveSession}
                            onClick={() => setShowMcpCatalog((current) => !current)}
                          >
                            {showMcpCatalog ? '收起' : '预选'}
                          </Button>
                        </div>
                        <p className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                          {MCP_RUNTIME_NOTE}
                        </p>
                        <p className={cn(
                          'mb-3 rounded-md border px-3 py-2 text-xs',
                          hasMcpSelectionChanges
                            ? 'border-blue-200 bg-blue-50 text-blue-700'
                            : 'border-border bg-secondary/40 text-muted-foreground',
                        )}>
                          {mcpSaveHint}
                        </p>
                        {selectedMcpSummaryItems.length > 0 && (
                          <div className="mb-3 rounded-md border border-primary/20 bg-primary/5 px-3 py-2">
                            <p className="text-xs font-medium text-foreground">已预选 MCP</p>
                            <div className="mt-2 flex flex-wrap gap-1.5">
                              {selectedMcpSummaryItems.map((item) => (
                                <span key={item.id} className="rounded-full bg-background px-2 py-0.5 text-xs text-muted-foreground shadow-sm">
                                  {item.name}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                        {showMcpCatalog && (
                          <div className="mb-3 flex justify-end">
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              disabled={!canSaveMcpBindings}
                              onClick={() => activeSession && saveMcpBindingsMutation.mutate({
                                sessionId: activeSession.id,
                              serverIds: selectedMcpServerIds,
                            })}
                          >
                              {saveMcpBindingsMutation.isPending && <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
                              保存预选 MCP
                            </Button>
                          </div>
                        )}
                        <div className="space-y-3">
                          {!showMcpCatalog && selectedMcpSummaryItems.length === 0 && (
                            <p className="text-sm text-muted-foreground">未预选 MCP 服务；当前预选仅记录会话偏好。</p>
                          )}
                          {mcpCatalogQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取 MCP 服务...</p>}
                          {showMcpCatalog && mcpCatalogQuery.isError && (
                            <p role="alert" className="text-sm text-red-600">MCP 服务读取失败：{displayError(mcpCatalogQuery.error)}</p>
                          )}
                          {showMcpCatalog && !mcpCatalogQuery.isLoading && !mcpCatalogQuery.isError && mcpCatalog.length === 0 && (
                            <p className="text-sm text-muted-foreground">暂无可选 MCP 服务。</p>
                          )}
                          {showMcpCatalog && mcpCatalog.map((server) => {
                            const checked = selectedMcpServerIds.includes(server.id)
                            return (
                              <label
                                key={server.id}
                                className={cn(
                                  'block rounded-lg border p-3 text-sm transition-colors',
                                  checked ? 'border-primary/50 bg-primary/5' : 'border-border/70 bg-background hover:border-primary/30',
                                )}
                              >
                                <span className="flex items-start gap-3">
                                  <input
                                    type="checkbox"
                                    className="mt-1 h-4 w-4 rounded border-border accent-primary"
                                    aria-label={`选择 ${server.name}`}
                                    checked={checked}
                                    disabled={!canAdjustMcpBindings || !server.runtimeCandidate}
                                    onChange={() => toggleMcpServer(server.id)}
                                  />
                                  <span className="min-w-0 flex-1">
                                    <span className="flex flex-wrap items-center gap-2">
                                      <span className="truncate font-semibold">{server.name}</span>
                                      {!server.runtimeCandidate && (
                                        <span className="rounded-full border border-border bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                                          暂不可用
                                        </span>
                                      )}
                                    </span>
                                    {server.description && (
                                      <span className="mt-1 line-clamp-2 block text-xs text-muted-foreground">{server.description}</span>
                                    )}
                                    <span className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
                                      <span>{server.tools.length} 个工具</span>
                                      <span>{server.resources.length} 个资源</span>
                                      <span>{server.prompts.length} 个提示</span>
                                    </span>
                                    {mcpUnavailableReason(server) && (
                                      <span className="mt-2 block text-xs text-amber-700">{mcpUnavailableReason(server)}</span>
                                    )}
                                  </span>
                                </span>
                              </label>
                            )
                          })}
                        </div>
                      </section>

                      <section className="rounded-lg border border-border/80 bg-card p-4 shadow-sm">
                        <div className="mb-3 flex items-center justify-between gap-3">
                          <div className="flex items-center gap-2">
                            <ShieldAlert className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                            <h2 className="text-base font-semibold">待审批工具</h2>
                          </div>
                          <span className="rounded-full border border-border bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                            {approvals.length}
                          </span>
                        </div>
                        <div className="space-y-3">
                          {approvalsQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取审批请求...</p>}
                          {approvalsQuery.isError && (
                            <p role="alert" className="text-sm text-red-600">审批请求读取失败：{displayError(approvalsQuery.error)}</p>
                          )}
                          {!approvalsQuery.isLoading && !approvalsQuery.isError && approvals.length === 0 && (
                            <p className="text-sm text-muted-foreground">暂无待审批工具。</p>
                          )}
                          {approvals.map((approval: WorkbenchToolApproval) => (
                            <div key={approval.id} className="space-y-3 rounded-lg border border-border/70 bg-background p-3">
                              <div className="flex flex-wrap items-center justify-between gap-2">
                                <div className="min-w-0">
                                  <p className="truncate text-sm font-semibold">{approval.toolName}</p>
                                  <p className="mt-0.5 truncate text-xs text-muted-foreground">
                                    {approval.mcpServerId || '未关联 MCP 服务'}
                                  </p>
                                </div>
                                <span className={cn('shrink-0 rounded-full border px-2 py-0.5 text-xs font-semibold', riskLevelClassName(approval.riskLevel))}>
                                  {riskLevelLabel(approval.riskLevel)}
                                </span>
                              </div>
                              <pre className="max-h-32 overflow-auto rounded-md bg-secondary/40 p-2 text-xs text-muted-foreground">
                                {formatRedactedArguments(approval.argumentsRedactedJson)}
                              </pre>
                              <div className="flex justify-end gap-2">
                                <Button
                                  type="button"
                                  size="sm"
                                  variant="outline"
                                  className="gap-2"
                                  disabled={!canUseActiveSession || decideApprovalMutation.isPending}
                                  onClick={() => activeSession && decideApprovalMutation.mutate({
                                    sessionId: activeSession.id,
                                    approvalId: approval.id,
                                    decision: 'reject',
                                  })}
                                >
                                  <X className="h-4 w-4" aria-hidden="true" />
                                  拒绝
                                </Button>
                                <Button
                                  type="button"
                                  size="sm"
                                  className="gap-2"
                                  disabled={!canUseActiveSession || decideApprovalMutation.isPending}
                                  onClick={() => activeSession && decideApprovalMutation.mutate({
                                    sessionId: activeSession.id,
                                    approvalId: approval.id,
                                    decision: 'approve',
                                  })}
                                >
                                  <Check className="h-4 w-4" aria-hidden="true" />
                                  批准
                                </Button>
                              </div>
                            </div>
                          ))}
                        </div>
                      </section>

                      <details className="rounded-lg border border-border/80 bg-card p-4 shadow-sm">
                        <summary className="flex cursor-pointer list-none items-center justify-between gap-3">
                          <span className="flex items-center gap-2">
                            <History className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                            <span className="text-base font-semibold">审计事件</span>
                          </span>
                          <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                            {(eventsQuery.data ?? []).length}
                          </span>
                        </summary>
                        <div className="mt-4 max-h-80 space-y-3 overflow-auto pr-1">
                          {eventsQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取事件...</p>}
                          {eventsQuery.isError && (
                            <p role="alert" className="text-sm text-red-600">事件读取失败：{displayError(eventsQuery.error)}</p>
                          )}
                          {(eventsQuery.data ?? []).length === 0 && !eventsQuery.isLoading && !eventsQuery.isError && (
                            <p className="text-sm text-muted-foreground">暂无事件。</p>
                          )}
                          {(eventsQuery.data ?? []).map((event) => (
                            <WorkbenchEventCard key={event.eventId} event={event} />
                          ))}
                        </div>
                      </details>
                    </div>
                  </TabsContent>
                </div>
              </div>

              <div className="border-t border-border/70 bg-background/95 px-6 py-4">
                <div className="mx-auto w-full max-w-[1120px] space-y-3">
                  {lastRun?.status === 'FAILED' && (
                    <div className="rounded-lg border border-destructive/25 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                      <p className="font-medium">模型调用失败</p>
                      <p className="mt-1 text-xs">{lastRun.message ?? '请检查模型配置或稍后重试。'}</p>
                    </div>
                  )}
                  <div className="flex items-end gap-2 rounded-[28px] border border-border bg-background px-3 py-2 shadow-sm transition-colors focus-within:border-primary/40 focus-within:ring-2 focus-within:ring-primary/10">
                    <Textarea
                      aria-label="给工作台发送消息"
                      value={message}
                      onChange={(event) => setMessage(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key !== 'Enter' || event.shiftKey || event.nativeEvent.isComposing) return
                        event.preventDefault()
                        sendCurrentMessage()
                      }}
                      placeholder="例如：请补充 README 的使用说明"
                      className="min-h-[48px] max-h-36 flex-1 resize-none border-0 bg-transparent px-2 py-3 shadow-none focus-visible:ring-0"
                    />
                    <Button
                      type="button"
                      size="icon"
                      className="mb-1 h-10 w-10 rounded-full"
                      disabled={!canSendMessage || sendMessageMutation.isPending}
                      onClick={sendCurrentMessage}
                      aria-label="发送消息"
                    >
                      {sendMessageMutation.isPending
                        ? <Loader2 className="h-4 w-4 animate-spin" aria-hidden="true" />
                        : <Send className="h-4 w-4" aria-hidden="true" />}
                      <span className="sr-only">发送消息</span>
                    </Button>
                  </div>
                </div>
              </div>
            </Tabs>
          </div>
        </main>

        <aside className="hidden min-h-0 overflow-auto border-l border-border/70 bg-[#fbfcfd] px-4 py-4 xl:order-3 xl:block">
          <div className="space-y-4">
            <section className="rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <div className="mb-4 flex items-center gap-2">
                <MessageSquare className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                <h2 className="text-base font-semibold">模型与运行时</h2>
              </div>
              <div className={cn('rounded-lg border px-3 py-2 text-sm', runtimeConfigTone(runtimeConfig))}>
                <p className="font-semibold">{runtimeConfigTitle(runtimeConfig)}</p>
                <p className="mt-1 text-xs leading-5">
                  {runtimeConfigQuery.isError ? '模型配置读取失败，请检查服务配置。' : runtimeConfig?.message ?? '正在读取模型配置状态。'}
                </p>
              </div>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center justify-between gap-3 border-b border-border/60 pb-3">
                  <dt className="text-muted-foreground">执行器</dt>
                  <dd className="font-semibold">{runtimeConfig?.modelExecutorEnabled ? '已启用' : '未启用'}</dd>
                </div>
                <div className="flex items-center justify-between gap-3">
                  <dt className="text-muted-foreground">模型地址</dt>
                  <dd className="font-semibold">{runtimeConfig?.modelBaseUrlConfigured ? '已配置' : '未配置'}</dd>
                </div>
              </dl>
            </section>

            <section className="rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <FileText className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  <h2 className="text-base font-semibold">文件</h2>
                </div>
                <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">{files.length}</span>
              </div>
              <div className="max-h-[320px] space-y-2 overflow-auto pr-1">
                {filesQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取文件列表...</p>}
                {filesQuery.isError && (
                  <p role="alert" className="text-sm text-red-600">文件列表读取失败：{displayError(filesQuery.error)}</p>
                )}
                {!filesQuery.isLoading && !filesQuery.isError && files.length === 0 && <p className="text-sm text-muted-foreground">暂无文件。更新技能会话可先导入源版本。</p>}
                {files.map((file) => (
                  <button
                    key={file.path}
                    type="button"
                    className={cn(
                      'flex w-full items-start justify-between gap-3 rounded-lg border px-3 py-2 text-left text-sm transition-colors',
                      selectedFile?.path === file.path
                        ? 'border-primary/50 bg-primary/5 text-foreground'
                        : 'border-border/70 bg-background hover:border-primary/30',
                    )}
                    onClick={() => selectFile(file)}
                  >
                    <span className="min-w-0">
                      <span className="block truncate font-medium">{file.path}</span>
                      <span className="block truncate text-xs text-muted-foreground">{file.contentType ?? 'unknown'}</span>
                    </span>
                    <span className="shrink-0 text-xs text-muted-foreground">{formatBytes(file.sizeBytes)}</span>
                  </button>
                ))}
              </div>
            </section>

            <section className="rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Cable className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  <h2 className="text-base font-semibold">MCP 服务</h2>
                </div>
                <Button
                  type="button"
                  size="sm"
                  variant="outline"
                  disabled={!canUseActiveSession}
                  onClick={() => setShowMcpCatalog((current) => !current)}
                >
                  {showMcpCatalog ? '收起 MCP 服务' : '预选 MCP 服务'}
                </Button>
              </div>
              <p className="mb-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-800">
                {MCP_RUNTIME_NOTE}
              </p>
              <p className={cn(
                'mb-3 rounded-md border px-3 py-2 text-xs',
                hasMcpSelectionChanges
                  ? 'border-blue-200 bg-blue-50 text-blue-700'
                  : 'border-border bg-secondary/40 text-muted-foreground',
              )}>
                {mcpSaveHint}
              </p>
              {selectedMcpSummaryItems.length > 0 && (
                <div className="mb-3 rounded-md border border-primary/20 bg-primary/5 px-3 py-2">
                  <p className="text-xs font-medium text-foreground">已预选 MCP</p>
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {selectedMcpSummaryItems.map((item) => (
                      <span key={item.id} className="rounded-full bg-background px-2 py-0.5 text-xs text-muted-foreground shadow-sm">
                        {item.name}
                      </span>
                    ))}
                  </div>
                </div>
              )}
              {showMcpCatalog && (
                <div className="mb-3 flex justify-end">
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    disabled={!canSaveMcpBindings}
                    onClick={() => activeSession && saveMcpBindingsMutation.mutate({
                      sessionId: activeSession.id,
                      serverIds: selectedMcpServerIds,
                    })}
                  >
                    {saveMcpBindingsMutation.isPending && <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" aria-hidden="true" />}
                    保存预选 MCP
                  </Button>
                </div>
              )}
              <div className="max-h-[320px] space-y-3 overflow-auto pr-1">
                {!showMcpCatalog && selectedMcpSummaryItems.length === 0 && (
                  <p className="text-sm text-muted-foreground">未预选 MCP 服务；当前预选仅记录会话偏好。</p>
                )}
                {mcpCatalogQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取 MCP 服务...</p>}
                {showMcpCatalog && mcpCatalogQuery.isError && (
                  <p role="alert" className="text-sm text-red-600">MCP 服务读取失败：{displayError(mcpCatalogQuery.error)}</p>
                )}
                {showMcpCatalog && !mcpCatalogQuery.isLoading && !mcpCatalogQuery.isError && mcpCatalog.length === 0 && (
                  <p className="text-sm text-muted-foreground">暂无可选 MCP 服务。</p>
                )}
                {showMcpCatalog && mcpCatalog.map((server) => {
                  const checked = selectedMcpServerIds.includes(server.id)
                  return (
                    <label
                      key={server.id}
                      className={cn(
                        'block rounded-lg border p-3 text-sm transition-colors',
                        checked ? 'border-primary/50 bg-primary/5' : 'border-border/70 bg-background hover:border-primary/30',
                      )}
                    >
                      <span className="flex items-start gap-3">
                          <input
                            type="checkbox"
                            className="mt-1 h-4 w-4 rounded border-border accent-primary"
                            aria-label={`选择 ${server.name}`}
                            checked={checked}
                            disabled={!canAdjustMcpBindings || !server.runtimeCandidate}
                            onChange={() => toggleMcpServer(server.id)}
                          />
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-2">
                            <span className="truncate font-semibold">{server.name}</span>
                            {!server.runtimeCandidate && (
                              <span className="rounded-full border border-border bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                                暂不可用
                              </span>
                            )}
                          </span>
                          {server.description && (
                            <span className="mt-1 line-clamp-2 block text-xs text-muted-foreground">{server.description}</span>
                          )}
                          <span className="mt-2 flex flex-wrap gap-2 text-xs text-muted-foreground">
                            <span>{server.tools.length} 个工具</span>
                            <span>{server.resources.length} 个资源</span>
                            <span>{server.prompts.length} 个提示</span>
                          </span>
                          {server.tags.length > 0 && (
                            <span className="mt-2 flex flex-wrap gap-1.5">
                              {server.tags.slice(0, 4).map((tag) => (
                                <span key={tag} className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                                  {tag}
                                </span>
                              ))}
                            </span>
                          )}
                          {mcpUnavailableReason(server) && (
                            <span className="mt-2 block text-xs text-amber-700">{mcpUnavailableReason(server)}</span>
                          )}
                        </span>
                      </span>
                    </label>
                  )
                })}
              </div>
            </section>

            <section className="rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <ShieldAlert className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  <h2 className="text-base font-semibold">待审批工具</h2>
                </div>
                <span className="rounded-full border border-border bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                  {approvals.length}
                </span>
              </div>
              <div className="space-y-3">
                {approvalsQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取审批请求...</p>}
                {approvalsQuery.isError && (
                  <p role="alert" className="text-sm text-red-600">审批请求读取失败：{displayError(approvalsQuery.error)}</p>
                )}
                {!approvalsQuery.isLoading && !approvalsQuery.isError && approvals.length === 0 && (
                  <p className="text-sm text-muted-foreground">暂无待审批工具。</p>
                )}
                {approvals.map((approval: WorkbenchToolApproval) => (
                  <div key={approval.id} className="space-y-3 rounded-lg border border-border/70 bg-background p-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold">{approval.toolName}</p>
                        <p className="mt-0.5 truncate text-xs text-muted-foreground">
                          {approval.mcpServerId || '未关联 MCP 服务'}
                        </p>
                      </div>
                      <span className={cn('shrink-0 rounded-full border px-2 py-0.5 text-xs font-semibold', riskLevelClassName(approval.riskLevel))}>
                        {riskLevelLabel(approval.riskLevel)}
                      </span>
                    </div>
                    <pre className="max-h-32 overflow-auto rounded-md bg-secondary/40 p-2 text-xs text-muted-foreground">
                      {formatRedactedArguments(approval.argumentsRedactedJson)}
                    </pre>
                    <div className="flex justify-end gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="outline"
                        className="gap-2"
                        disabled={!canUseActiveSession || decideApprovalMutation.isPending}
                        onClick={() => activeSession && decideApprovalMutation.mutate({
                          sessionId: activeSession.id,
                          approvalId: approval.id,
                          decision: 'reject',
                        })}
                      >
                        <X className="h-4 w-4" aria-hidden="true" />
                        拒绝
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        className="gap-2"
                        disabled={!canUseActiveSession || decideApprovalMutation.isPending}
                        onClick={() => activeSession && decideApprovalMutation.mutate({
                          sessionId: activeSession.id,
                          approvalId: approval.id,
                          decision: 'approve',
                        })}
                      >
                        <Check className="h-4 w-4" aria-hidden="true" />
                        批准
                      </Button>
                    </div>
                  </div>
                ))}
              </div>
            </section>

            <details className="rounded-lg border border-border/70 bg-background p-4 shadow-sm">
              <summary className="flex cursor-pointer list-none items-center justify-between gap-3">
                <span className="flex items-center gap-2">
                  <History className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                  <span className="text-base font-semibold">审计事件</span>
                </span>
                <span className="rounded-full bg-secondary px-2 py-0.5 text-xs text-muted-foreground">
                  {(eventsQuery.data ?? []).length}
                </span>
              </summary>
              <div className="mt-4 flex items-center gap-2">
                <History className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                <h2 className="text-base font-semibold">事件</h2>
              </div>
              <div className="mt-4 max-h-[360px] space-y-3 overflow-auto pr-1">
                {eventsQuery.isLoading && <p className="text-sm text-muted-foreground">正在读取事件...</p>}
                {eventsQuery.isError && (
                  <p role="alert" className="text-sm text-red-600">事件读取失败：{displayError(eventsQuery.error)}</p>
                )}
                {(eventsQuery.data ?? []).length === 0 && !eventsQuery.isLoading && !eventsQuery.isError && (
                  <p className="text-sm text-muted-foreground">暂无事件。</p>
                )}
                {(eventsQuery.data ?? []).map((event) => (
                  <WorkbenchEventCard key={event.eventId} event={event} />
                ))}
              </div>
            </details>
          </div>
        </aside>
      </div>
    </div>
  )
}

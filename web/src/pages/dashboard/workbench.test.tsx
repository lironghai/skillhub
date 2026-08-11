/** @vitest-environment jsdom */
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  CreateWorkbenchSessionRequest,
  ManagedNamespace,
  SkillSummary,
  SkillVersion,
  WorkbenchDiffResult,
  WorkbenchFile,
  WorkbenchFileContent,
  WorkbenchMcpBinding,
  WorkbenchMcpCatalogItem,
  WorkbenchPackagePreview,
  WorkbenchPublishResult,
  WorkbenchRuntimeConfig,
  WorkbenchRuntimeRunResponse,
  WorkbenchSession,
  WorkbenchSessionEvent,
  WorkbenchToolApproval,
} from '@/api/types'

const workbenchApiMock = vi.hoisted(() => ({
  getRuntimeConfig: vi.fn(),
  listSessions: vi.fn(),
  createSession: vi.fn(),
  getSession: vi.fn(),
  importSource: vi.fn(),
  listEvents: vi.fn(),
  listFiles: vi.fn(),
  readFile: vi.fn(),
  writeFile: vi.fn(),
  deleteFile: vi.fn(),
  getDiff: vi.fn(),
  readyForReview: vi.fn(),
  packagePreview: vi.fn(),
  publishPackage: vi.fn(),
  sendMessage: vi.fn(),
  sendMessageStream: vi.fn(),
  cancelRun: vi.fn(),
  listMcpCatalog: vi.fn(),
  listMcpBindings: vi.fn(),
  saveMcpBindings: vi.fn(),
  listApprovals: vi.fn(),
  approveApproval: vi.fn(),
  rejectApproval: vi.fn(),
}))

const namespaceApiMock = vi.hoisted(() => ({
  listMine: vi.fn(),
}))

const meApiMock = vi.hoisted(() => ({
  getSkills: vi.fn(),
}))

const skillLifecycleApiMock = vi.hoisted(() => ({
  listVersions: vi.fn(),
}))

vi.mock('@/api/client', () => ({
  workbenchApi: workbenchApiMock,
  namespaceApi: namespaceApiMock,
  meApi: meApiMock,
  skillLifecycleApi: skillLifecycleApiMock,
}))

vi.mock('@/shared/components/dashboard-page-header', () => ({
  DashboardPageHeader: ({ title, subtitle }: { title: string; subtitle: string }) => (
    <header>
      <h1>{title}</h1>
      <p>{subtitle}</p>
    </header>
  ),
}))

vi.mock('@/shared/ui/select', () => {
  function Select({
    value,
    onValueChange,
    disabled,
    children,
  }: {
    value?: string
    onValueChange?: (value: string) => void
    disabled?: boolean
    children: ReactNode
  }) {
    const options: Array<{ value: string; label: string }> = []
    let label = 'select'
    const collectOptions = (node: ReactNode) => {
      if (!node) return
      if (Array.isArray(node)) {
        node.forEach(collectOptions)
        return
      }
      if (typeof node === 'object' && 'props' in node) {
        const element = node as { props: { value?: string; children?: ReactNode; 'aria-label'?: string } }
        if (element.props['aria-label']) {
          label = element.props['aria-label']
        }
        if (element.props.value) {
          options.push({ value: element.props.value, label: String(element.props.children) })
        }
        collectOptions(element.props.children)
      }
    }
    collectOptions(children)
    return (
      <select aria-label={label} value={value ?? ''} disabled={disabled} onChange={(event) => onValueChange?.(event.target.value)}>
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>
    )
  }

  return {
    Select,
    SelectContent: ({ children }: { children: ReactNode }) => <>{children}</>,
    SelectItem: ({ children }: { children: ReactNode }) => <>{children}</>,
    SelectTrigger: ({ children }: { children: ReactNode }) => <>{children}</>,
    SelectValue: () => null,
    normalizeSelectValue: (value?: string | null) => value || undefined,
  }
})

import { WorkbenchPage } from './workbench'

const session: WorkbenchSession = {
  id: 7,
  status: 'DRAFT',
  mode: 'UPDATE_SKILL',
  namespaceId: 3,
  targetSlug: 'knowledge-helper',
  targetVersion: '1.2.0',
  fileCount: 2,
  expiresAt: '2026-08-01T00:00:00Z',
}

const namespaces: ManagedNamespace[] = [
  {
    id: 3,
    slug: 'team-ai',
    displayName: 'Team AI',
    type: 'TEAM',
    status: 'ACTIVE',
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-01T00:00:00Z',
    immutable: false,
    canFreeze: true,
    canUnfreeze: false,
    canArchive: true,
    canRestore: false,
    canDelete: true,
  },
]

const sourceSkills: SkillSummary[] = [
  {
    id: 31,
    slug: 'knowledge-helper',
    displayName: 'Knowledge Helper',
    summary: 'Help write docs',
    downloadCount: 2,
    starCount: 1,
    ratingCount: 0,
    namespace: 'team-ai',
    updatedAt: '2026-07-31T00:00:00Z',
    canSubmitPromotion: false,
  },
]

const sourceVersions: SkillVersion[] = [
  {
    id: 41,
    version: '1.1.0',
    status: 'PUBLISHED',
    fileCount: 2,
    totalSize: 128,
    publishedAt: '2026-07-30T00:00:00Z',
    downloadAvailable: true,
  },
]

const runtimeConfig: WorkbenchRuntimeConfig = {
  modelExecutorEnabled: false,
  modelConfigured: false,
  modelProvider: null,
  modelName: null,
  modelBaseUrlConfigured: false,
  displayStatus: 'MODEL_NOT_CONFIGURED',
  message: '模型执行器未配置；当前只创建 AgentScope 会话上下文，不会调用真实模型。',
}

const files: WorkbenchFile[] = [
  { path: 'SKILL.md', sizeBytes: 128, contentType: 'text/markdown; charset=utf-8' },
  { path: 'assets/logo.png', sizeBytes: 512, contentType: 'image/png' },
]

const skillFile: WorkbenchFileContent = {
  path: 'SKILL.md',
  content: '# Knowledge Helper\n',
  contentType: 'text/markdown; charset=utf-8',
  sizeBytes: 19,
}

const events: WorkbenchSessionEvent[] = [
  {
    eventId: 1,
    type: 'AUDIT',
    createdAt: '2026-07-31T08:00:00Z',
    payloadJson: '{"action":"session.created"}',
  },
]

const chatEvents: WorkbenchSessionEvent[] = [
  {
    eventId: 2,
    type: 'USER_MESSAGE',
    createdAt: '2026-07-31T08:01:00Z',
    payloadJson: '{"message":"先帮我梳理技能目标"}',
  },
  {
    eventId: 3,
    type: 'MODEL_MESSAGE',
    createdAt: '2026-07-31T08:01:01Z',
    payloadJson: '{"message":"可以，我会先确认目标、输入和输出格式。"}',
  },
]

const diff: WorkbenchDiffResult = {
  files: [
    { path: 'SKILL.md', status: 'MODIFIED' },
    { path: 'references/new.md', status: 'ADDED' },
  ],
}

const packagePreview: WorkbenchPackagePreview = {
  packageFingerprint: 'sha256:ready-package',
  readyToPublish: true,
  includedFiles: [
    { path: 'SKILL.md', sizeBytes: 128, sha256: 'sha256:skill' },
    { path: 'references/guide.md', sizeBytes: 256, sha256: 'sha256:guide' },
  ],
  excludedFiles: [
    { path: 'assets/private.key', reason: 'unsupported extension' },
  ],
  validation: {
    status: 'PASSED',
    messages: ['manifest ok', 'policy ok'],
  },
}

const publishResult: WorkbenchPublishResult = {
  skillId: 31,
  skillVersionId: 41,
  namespace: 'team-ai',
  slug: 'knowledge-helper',
  version: '1.2.0',
  status: 'PUBLISHED',
}

const mcpCatalog: WorkbenchMcpCatalogItem[] = [
  {
    id: 'ctx-tools',
    name: 'Context Tools',
    description: '上下文查询工具',
    enabled: true,
    tools: [
      { id: 'search-docs', name: 'Search Docs' },
      { id: 'read-doc', name: 'Read Doc' },
      { id: 'list-docs', name: 'List Docs' },
    ],
    resources: [
      { id: 'docs', name: 'Docs' },
    ],
    prompts: [],
    tags: ['知识库', '只读'],
    catalogSource: 'CONTEXT_FORGE',
    runtimeCandidate: true,
  },
  {
    id: 'ticket-tools',
    name: 'Ticket Tools',
    description: '工单操作工具',
    enabled: true,
    tools: [
      { id: 'update-ticket', name: 'Update Ticket' },
      { id: 'close-ticket', name: 'Close Ticket' },
    ],
    resources: [],
    prompts: [
      { id: 'ticket-summary', name: 'Ticket Summary' },
    ],
    tags: ['工单'],
    catalogSource: 'CONTEXT_FORGE',
    runtimeCandidate: true,
  },
]

const mcpBindings: WorkbenchMcpBinding[] = [
  {
    id: 11,
    serverId: 'ctx-tools',
    catalogSource: 'CONTEXT_FORGE',
    enabledToolsJson: ['search_docs'],
    disabledToolsJson: [],
    toolPolicyJson: {},
    policyVersion: 'v1',
    status: 'ENABLED',
  },
]

const approvals: WorkbenchToolApproval[] = [
  {
    id: 21,
    sessionId: 7,
    eventId: 9,
    toolName: 'update_ticket',
    mcpServerId: 'ticket-tools',
    riskLevel: 'MUTATING',
    argumentsRedactedJson: { ticketId: '[REDACTED]', token: '[REDACTED]' },
    status: 'PENDING',
    decisionBy: null,
    decisionAt: null,
    createdAt: '2026-07-31T08:10:00Z',
  },
]

function renderWorkbench() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <WorkbenchPage />
    </QueryClientProvider>,
  )
}

async function openRecentSession(sessionId = 7, expectedText: RegExp | string = /knowledge-helper/) {
  void sessionId
  const sessionText = await screen.findByText(expectedText)
  fireEvent.click(sessionText.closest('button')!)
  await screen.findByText(expectedText)
}

async function openSessionSetup() {
  fireEvent.click(await screen.findByRole('button', { name: '新建工作会话' }))
  await screen.findByText('Team AI (@team-ai)')
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (error: Error) => void
  const promise = new Promise<T>((promiseResolve, promiseReject) => {
    resolve = promiseResolve
    reject = promiseReject
  })
  return { promise, resolve, reject }
}

describe('WorkbenchPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    namespaceApiMock.listMine.mockResolvedValue(namespaces)
    meApiMock.getSkills.mockResolvedValue({ items: sourceSkills, total: sourceSkills.length, page: 0, size: 100 })
    skillLifecycleApiMock.listVersions.mockResolvedValue({ items: sourceVersions, total: sourceVersions.length, page: 0, size: 100 })
    workbenchApiMock.getRuntimeConfig.mockResolvedValue(runtimeConfig)
    workbenchApiMock.listSessions.mockResolvedValue([session])
    workbenchApiMock.createSession.mockResolvedValue(session)
    workbenchApiMock.getSession.mockResolvedValue(session)
    workbenchApiMock.importSource.mockResolvedValue({ importedFiles: files.map((file) => file.path) })
    workbenchApiMock.listEvents.mockResolvedValue(events)
    workbenchApiMock.listFiles.mockResolvedValue(files)
    workbenchApiMock.readFile.mockResolvedValue(skillFile)
    workbenchApiMock.writeFile.mockImplementation((_sessionId: number, path: string, request: { content: string; contentType?: string }) =>
      Promise.resolve({
        path,
        content: request.content,
        contentType: request.contentType ?? 'text/markdown; charset=utf-8',
        sizeBytes: request.content.length,
      }),
    )
    workbenchApiMock.getDiff.mockResolvedValue(diff)
    workbenchApiMock.readyForReview.mockResolvedValue({
      ...session,
      status: 'READY_FOR_REVIEW',
    })
    workbenchApiMock.packagePreview.mockResolvedValue(packagePreview)
    workbenchApiMock.publishPackage.mockResolvedValue(publishResult)
    workbenchApiMock.sendMessage.mockResolvedValue({
      runId: 'run-1',
      status: 'COMPLETED',
      message: '模型已响应',
      eventCount: 3,
    } satisfies WorkbenchRuntimeRunResponse)
    workbenchApiMock.sendMessageStream.mockImplementation(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-1' } })
      onEvent({ event: 'message_delta', data: { content: '模型回复' } })
      onEvent({ event: 'completed', data: { runId: 'run-1', status: 'COMPLETED', message: '模型已响应', eventCount: 3 } })
    })
    workbenchApiMock.listMcpCatalog.mockResolvedValue({ items: mcpCatalog, total: mcpCatalog.length, page: 0, size: 20 })
    workbenchApiMock.listMcpBindings.mockResolvedValue(mcpBindings)
    workbenchApiMock.saveMcpBindings.mockResolvedValue(mcpBindings)
    workbenchApiMock.listApprovals.mockResolvedValue(approvals)
    workbenchApiMock.approveApproval.mockResolvedValue(undefined)
    workbenchApiMock.rejectApproval.mockResolvedValue(undefined)
  })

  afterEach(() => cleanup())

  it('submits CREATE_SKILL form fields when creating a session', async () => {
    renderWorkbench()

    await openSessionSetup()
    fireEvent.change(screen.getByLabelText('目标标识'), { target: { value: 'new-skill' } })
    fireEvent.change(screen.getByLabelText('目标版本'), { target: { value: '0.1.0' } })
    fireEvent.change(screen.getByLabelText('有效小时'), { target: { value: '48' } })
    fireEvent.click(screen.getByRole('button', { name: '开始工作会话' }))

    await waitFor(() => expect(workbenchApiMock.createSession).toHaveBeenCalled())
    expect(workbenchApiMock.createSession).toHaveBeenCalledWith({
      namespace: 'team-ai',
      mode: 'CREATE_SKILL',
      targetSlug: 'new-skill',
      targetVersion: '0.1.0',
      expiresInHours: 48,
    } satisfies CreateWorkbenchSessionRequest)
  })

  it('shows UPDATE_SKILL source skill fields and submits them', async () => {
    renderWorkbench()

    await openSessionSetup()
    fireEvent.change(screen.getByLabelText('会话模式'), { target: { value: 'UPDATE_SKILL' } })

    expect(screen.getByLabelText('源命名空间')).toBeTruthy()
    expect(screen.getByLabelText('技能')).toBeTruthy()
    expect(screen.getByLabelText('源技能')).toBeTruthy()
    expect(screen.getByLabelText('源版本')).toBeTruthy()

    expect((await screen.findAllByText('Knowledge Helper (knowledge-helper)')).length).toBeGreaterThan(0)
    fireEvent.change(screen.getByLabelText('源技能'), { target: { value: 'knowledge-helper' } })
    await waitFor(() => expect(skillLifecycleApiMock.listVersions).toHaveBeenCalledWith('team-ai', 'knowledge-helper', { size: 100 }))
    fireEvent.change(screen.getByLabelText('源版本'), { target: { value: '1.1.0' } })
    const createButton = screen.getByRole('button', { name: '开始工作会话' }) as HTMLButtonElement
    await waitFor(() => expect(createButton.disabled).toBe(false))
    fireEvent.click(createButton)

    await waitFor(() => expect(workbenchApiMock.createSession).toHaveBeenCalled())
    expect(workbenchApiMock.createSession).toHaveBeenCalledWith(expect.objectContaining({
      mode: 'UPDATE_SKILL',
      namespace: 'team-ai',
      targetSlug: 'knowledge-helper',
      targetVersion: '1.1.1',
      sourceSkill: { namespace: 'team-ai', slug: 'knowledge-helper', version: '1.1.0' },
    }))
  })

  it('keeps session setup behind a lightweight entry and supports history search plus loading more', async () => {
    const manySessions = Array.from({ length: 20 }, (_, index) => ({
      ...session,
      id: index + 1,
      targetSlug: index === 1 ? 'docs-helper' : `history-${index + 1}`,
    }))
    workbenchApiMock.listSessions.mockImplementation(({ limit }: { limit?: number } = {}) =>
      Promise.resolve(manySessions.slice(0, limit ?? 20)),
    )
    renderWorkbench()

    expect(await screen.findByText('选择一个会话继续对话')).toBeTruthy()
    expect(screen.queryByText('会话目标设置')).toBeNull()
    expect(await screen.findByText('history-1')).toBeTruthy()

    fireEvent.change(screen.getByLabelText('搜索会话历史'), { target: { value: 'docs' } })
    expect(screen.getByText('docs-helper')).toBeTruthy()
    expect(screen.queryByText('history-1')).toBeNull()

    fireEvent.change(screen.getByLabelText('搜索会话历史'), { target: { value: '' } })
    fireEvent.click(await screen.findByRole('button', { name: '加载更多会话' }))
    await waitFor(() => expect(workbenchApiMock.listSessions).toHaveBeenCalledWith({ limit: 40 }))

    fireEvent.click(screen.getByRole('button', { name: '新建工作会话' }))
    expect(await screen.findByText('会话目标设置')).toBeTruthy()
  })

  it('reads a selected text file and writes editor changes', async () => {
    renderWorkbench()

    await openRecentSession()

    await screen.findByRole('button', { name: /SKILL\.md/ })
    fireEvent.click(screen.getByRole('button', { name: /SKILL\.md/ }))

    const editor = await screen.findByLabelText('文件内容')
    expect((editor as HTMLTextAreaElement).value).toBe('# Knowledge Helper\n')

    fireEvent.change(editor, { target: { value: '# Updated\n' } })
    fireEvent.click(screen.getByRole('button', { name: '保存文件' }))

    await waitFor(() => expect(workbenchApiMock.writeFile).toHaveBeenCalledWith(
      7,
      'SKILL.md',
      { content: '# Updated\n', contentType: 'text/markdown; charset=utf-8' },
    ))
  })

  it('ignores stale file reads after the user selects another file', async () => {
    const pendingRead = deferred<WorkbenchFileContent>()
    workbenchApiMock.readFile.mockReturnValueOnce(pendingRead.promise)
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(await screen.findByRole('button', { name: /SKILL\.md/ }))
    fireEvent.click(await screen.findByRole('button', { name: /assets\/logo\.png/ }))

    pendingRead.resolve(skillFile)

    expect(await screen.findByText('当前文件不是文本文件，Phase 4 仅支持 UTF-8 文本编辑。')).toBeTruthy()
    await waitFor(() => expect(screen.queryByLabelText('文件内容')).toBeNull())
  })

  it('ignores stale file read failures after the user selects another file', async () => {
    const pendingRead = deferred<WorkbenchFileContent>()
    workbenchApiMock.readFile.mockReturnValueOnce(pendingRead.promise)
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(await screen.findByRole('button', { name: /SKILL\.md/ }))
    fireEvent.click(await screen.findByRole('button', { name: /assets\/logo\.png/ }))

    const rejection = expect(pendingRead.promise).rejects.toThrow('read failed')
    pendingRead.reject(new Error('read failed'))
    await rejection

    await waitFor(() => expect(screen.queryByText('read failed')).toBeNull())
  })

  it('clears the open editor after importing the source version', async () => {
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(await screen.findByRole('button', { name: /SKILL\.md/ }))
    expect(await screen.findByLabelText('文件内容')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '导入源版本' }))

    await waitFor(() => expect(workbenchApiMock.importSource).toHaveBeenCalledWith(7))
    expect(screen.queryByLabelText('文件内容')).toBeNull()
  })

  it('allows UTF-8 config files whose content type is generic', async () => {
    const configFile = { path: 'settings.ini', sizeBytes: 16, contentType: 'application/octet-stream' }
    workbenchApiMock.listFiles.mockResolvedValue([configFile])
    workbenchApiMock.readFile.mockResolvedValue({
      path: 'settings.ini',
      content: 'enabled=true\n',
      contentType: 'application/octet-stream',
      sizeBytes: 13,
    } satisfies WorkbenchFileContent)
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(await screen.findByRole('button', { name: /settings\.ini/ }))

    expect(await screen.findByLabelText('文件内容')).toBeTruthy()
    expect(workbenchApiMock.readFile).toHaveBeenCalledWith(7, 'settings.ini')
  })

  it('sends a message without exposing internal runtime ids', async () => {
    renderWorkbench()

    await openRecentSession()

    const composer = await screen.findByLabelText('给工作台发送消息')
    fireEvent.change(composer, { target: { value: '请完善 README' } })
    fireEvent.keyDown(composer, { key: 'Enter', code: 'Enter' })

    expect(await screen.findByText('请完善 README')).toBeTruthy()
    expect((screen.getByLabelText('给工作台发送消息') as HTMLTextAreaElement).value).toBe('')
    await waitFor(() => expect(workbenchApiMock.sendMessageStream).toHaveBeenCalledWith(7, '请完善 README', expect.any(Function)))
    expect(screen.queryByText('同步中')).toBeNull()
    expect(screen.queryByText('run-1')).toBeNull()
    expect(screen.queryByText('模型已响应')).toBeNull()
  })

  it('shows one streaming assistant bubble with process details while a message is pending', async () => {
    const pendingRun = deferred<void>()
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-pending' } })
      onEvent({ event: 'thinking_delta', data: { content: '正在分析技能目标' } })
      return pendingRun.promise
    })
    renderWorkbench()

    await openRecentSession()

    const composer = await screen.findByLabelText('给工作台发送消息')
    fireEvent.change(composer, { target: { value: '请创建一个 SkillHub 使用指南技能' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByText('请创建一个 SkillHub 使用指南技能')).toBeTruthy()
    expect(screen.getByText('思考过程')).toBeTruthy()
    expect(screen.getByText(/正在分析技能目标/)).toBeTruthy()
    expect(screen.getAllByText('工作台').length).toBeGreaterThan(0)
    expect(screen.queryByText('最终回复')).toBeNull()
    expect(screen.getByRole('button', { name: '发送消息' })).toBeTruthy()

    pendingRun.resolve()
  })

  it('keeps streaming thinking, tools, and final text in one assistant message', async () => {
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-started' } })
      onEvent({ event: 'thinking_delta', data: { content: '先检查项目文件' } })
      onEvent({
        event: 'runtime_event',
        data: {
          type: 'TOOL_CALL',
          payloadJson: JSON.stringify({ toolName: 'list_files' }),
        },
      })
      onEvent({ event: 'message_delta', data: { content: '已经完成检查。' } })
      onEvent({ event: 'completed', data: { runId: 'run-started', status: 'COMPLETED', message: '模型已响应', eventCount: 4 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '检查技能文件' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByText('思考过程')).toBeTruthy()
    expect(screen.getByText(/先检查项目文件/)).toBeTruthy()
    expect(screen.getByText('工具调用（1）')).toBeTruthy()
    expect(screen.getAllByText('工具').length).toBeGreaterThan(0)
    expect(screen.getByText('调用工具：list_files')).toBeTruthy()
    expect(screen.getByText('已经完成检查。')).toBeTruthy()
    expect(screen.queryByText('最终回复')).toBeNull()
    expect(screen.queryByText('处理中')).toBeNull()
    expect(screen.queryByText('生成中')).toBeNull()
    await waitFor(() => {
      expect(screen.getByText('思考过程').closest('details')?.open).toBe(false)
      expect(screen.getByText('工具调用（1）').closest('details')?.open).toBe(false)
    })
  })

  it('renders an assembled streaming assistant response as markdown', async () => {
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-markdown' } })
      onEvent({ event: 'message_delta', data: { content: '## 实时结果\n\n- **MCP** ' } })
      onEvent({ event: 'message_delta', data: { content: '可用\n\n```json\n{"ok":true}\n```' } })
      onEvent({ event: 'completed', data: { runId: 'run-markdown', status: 'COMPLETED', message: '模型已响应', eventCount: 4 } })
    })
    const { container } = renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '输出 Markdown' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByRole('heading', { name: '实时结果' })).toBeTruthy()
    expect(screen.getByText('MCP').tagName).toBe('STRONG')
    expect(container.querySelector('code.language-json')?.textContent).toContain('{"ok":true}')
  })

  it('anchors stored assistant replies after their source user message', async () => {
    workbenchApiMock.listEvents.mockResolvedValue([
      {
        eventId: 10,
        type: 'USER_MESSAGE',
        createdAt: '2026-07-31T08:01:00Z',
        payloadJson: '{"message":"第一条用户问题"}',
      },
      {
        eventId: 11,
        type: 'USER_MESSAGE',
        createdAt: '2026-07-31T08:02:00Z',
        payloadJson: '{"message":"第二条用户问题"}',
      },
      {
        eventId: 12,
        type: 'MODEL_MESSAGE',
        createdAt: '2026-07-31T08:02:01Z',
        payloadJson: '{"message":"回复第一轮","sourceUserEventId":10,"sourceUserContent":"第一条用户问题"}',
      },
      {
        eventId: 13,
        type: 'MODEL_MESSAGE',
        createdAt: '2026-07-31T08:02:02Z',
        payloadJson: '{"message":"回复第二轮","sourceUserEventId":11,"sourceUserContent":"第二条用户问题"}',
      },
    ])
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('回复第一轮')
    const transcript = document.body.textContent ?? ''
    expect(transcript.indexOf('第一条用户问题')).toBeLessThan(transcript.indexOf('回复第一轮'))
    expect(transcript.indexOf('回复第一轮')).toBeLessThan(transcript.indexOf('第二条用户问题'))
    expect(transcript.indexOf('第二条用户问题')).toBeLessThan(transcript.indexOf('回复第二轮'))
  })

  it('renders stored assistant replies as markdown', async () => {
    workbenchApiMock.listEvents.mockResolvedValue([
      {
        eventId: 10,
        type: 'USER_MESSAGE',
        createdAt: '2026-07-31T08:01:00Z',
        payloadJson: '{"message":"请用 markdown 总结"}',
      },
      {
        eventId: 11,
        type: 'MODEL_MESSAGE',
        createdAt: '2026-07-31T08:01:01Z',
        payloadJson: JSON.stringify({
          message: '## 执行结果\n\n- 已检查 MCP 绑定\n\n| 项目 | 状态 |\n| --- | --- |\n| Markdown | 支持 |',
        }),
      },
    ])
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByRole('heading', { name: '执行结果' })).toBeTruthy()
    expect(screen.getByText('已检查 MCP 绑定')).toBeTruthy()
    expect(screen.getByRole('table')).toBeTruthy()
    expect(screen.getByText('Markdown')).toBeTruthy()
  })

  it('does not render a second assistant bubble when the stored final message only differs by trailing whitespace', async () => {
    const eventCalls: Array<unknown> = []
    workbenchApiMock.listEvents.mockImplementation(async () => {
      eventCalls.push(null)
      if (eventCalls.length === 1) return chatEvents
      return [
        ...chatEvents,
        {
          eventId: 4,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:02:00Z',
          payloadJson: '{"message":"检查技能文件"}',
        },
        {
          eventId: 5,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:02:03Z',
          payloadJson: '{"message":"已经完成检查。\\n"}',
        },
      ]
    })
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-started' } })
      onEvent({ event: 'thinking_delta', data: { content: '先检查项目文件' } })
      onEvent({ event: 'message_delta', data: { content: '已经完成检查。' } })
      onEvent({ event: 'completed', data: { runId: 'run-started', status: 'COMPLETED', message: '模型已响应', eventCount: 3 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '检查技能文件' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))
    expect(screen.getAllByText('已经完成检查。')).toHaveLength(1)
    expect(screen.getByText('思考过程')).toBeTruthy()
  })

  it('deduplicates stored assistant messages by runtime run id before comparing text content', async () => {
    const eventCalls: Array<unknown> = []
    workbenchApiMock.listEvents.mockImplementation(async () => {
      eventCalls.push(null)
      if (eventCalls.length === 1) return chatEvents
      return [
        ...chatEvents,
        {
          eventId: 4,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:02:00Z',
          payloadJson: '{"message":"检查技能文件"}',
        },
        {
          eventId: 5,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:02:03Z',
          payloadJson: '{"message":"已经完成检查，文件已经刷新。","runId":"run-linked"}',
        },
      ]
    })
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-linked' } })
      onEvent({ event: 'thinking_delta', data: { content: '先检查项目文件' } })
      onEvent({ event: 'message_delta', data: { content: '已经完成检查。' } })
      onEvent({ event: 'completed', data: { runId: 'run-linked', status: 'COMPLETED', message: '模型已响应', eventCount: 3 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '检查技能文件' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))
    expect(screen.getByText('已经完成检查。')).toBeTruthy()
    expect(screen.queryByText('已经完成检查，文件已经刷新。')).toBeNull()
    expect(screen.getByText('思考过程')).toBeTruthy()
  })

  it('does not use legacy text fallback across different user requests', async () => {
    const eventCalls: Array<unknown> = []
    workbenchApiMock.listEvents.mockImplementation(async () => {
      eventCalls.push(null)
      return [
        {
          eventId: 1,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:01:00Z',
          payloadJson: '{"message":"第一个问题"}',
        },
        {
          eventId: 2,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:01:03Z',
          payloadJson: '{"message":"相同回复"}',
        },
        {
          eventId: 3,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:02:00Z',
          payloadJson: '{"message":"第二个问题"}',
        },
      ]
    })
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-no-stored-id' } })
      onEvent({ event: 'message_delta', data: { content: '相同回复' } })
      onEvent({ event: 'completed', data: { runId: 'run-no-stored-id', status: 'COMPLETED', message: '模型已响应', eventCount: 2 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '第二个问题' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))
    expect(screen.getAllByText('相同回复')).toHaveLength(2)
  })

  it('only applies legacy text fallback to the latest matching user request', async () => {
    const eventCalls: Array<unknown> = []
    workbenchApiMock.listEvents.mockImplementation(async () => {
      eventCalls.push(null)
      if (eventCalls.length === 1) {
        return [
          {
            eventId: 1,
            type: 'USER_MESSAGE',
            createdAt: '2026-07-31T08:01:00Z',
            payloadJson: '{"message":"重复问题"}',
          },
          {
            eventId: 2,
            type: 'MODEL_MESSAGE',
            createdAt: '2026-07-31T08:01:03Z',
            payloadJson: '{"message":"重复回复"}',
          },
        ]
      }
      return [
        {
          eventId: 1,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:01:00Z',
          payloadJson: '{"message":"重复问题"}',
        },
        {
          eventId: 2,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:01:03Z',
          payloadJson: '{"message":"重复回复"}',
        },
        {
          eventId: 3,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:02:00Z',
          payloadJson: '{"message":"重复问题"}',
        },
        {
          eventId: 4,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:02:03Z',
          payloadJson: '{"message":"重复回复"}',
        },
      ]
    })
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'legacy-repeat' } })
      onEvent({ event: 'message_delta', data: { content: '重复回复' } })
      onEvent({ event: 'completed', data: { runId: 'legacy-repeat', status: 'COMPLETED', message: '模型已响应', eventCount: 2 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '重复问题' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))
    expect(screen.getAllByText('重复回复')).toHaveLength(2)
  })

  it('deduplicates stored tool messages by runtime run id after stream events are refetched', async () => {
    const eventCalls: Array<unknown> = []
    workbenchApiMock.listEvents.mockImplementation(async () => {
      eventCalls.push(null)
      if (eventCalls.length === 1) return chatEvents
      return [
        ...chatEvents,
        {
          eventId: 4,
          type: 'USER_MESSAGE',
          createdAt: '2026-07-31T08:02:00Z',
          payloadJson: '{"message":"检查技能文件"}',
        },
        {
          eventId: 5,
          type: 'TOOL_CALL',
          createdAt: '2026-07-31T08:02:02Z',
          payloadJson: '{"toolName":"list_files","runId":"run-tool-linked"}',
        },
        {
          eventId: 6,
          type: 'MODEL_MESSAGE',
          createdAt: '2026-07-31T08:02:03Z',
          payloadJson: '{"message":"已经完成检查。","runId":"run-tool-linked"}',
        },
      ]
    })
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-tool-linked' } })
      onEvent({
        event: 'runtime_event',
        data: {
          type: 'TOOL_CALL',
          payloadJson: JSON.stringify({ toolName: 'list_files', runId: 'run-tool-linked' }),
        },
      })
      onEvent({ event: 'message_delta', data: { content: '已经完成检查。' } })
      onEvent({ event: 'completed', data: { runId: 'run-tool-linked', status: 'COMPLETED', message: '模型已响应', eventCount: 3 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '检查技能文件' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))
    expect(screen.getAllByText('调用工具：list_files')).toHaveLength(1)
    expect(screen.getByText('已经完成检查。')).toBeTruthy()
  })

  it('hides the pending thinking placeholder after the final response event arrives', async () => {
    workbenchApiMock.listEvents.mockResolvedValue([
      ...chatEvents,
      {
        eventId: 4,
        type: 'USER_MESSAGE',
        createdAt: '2026-07-31T08:02:00Z',
        payloadJson: '{"message":"继续完善"}',
      },
      {
        eventId: 5,
        type: 'MODEL_MESSAGE',
        createdAt: '2026-07-31T08:02:03Z',
        payloadJson: '{"message":"已经补充完成。"}',
      },
    ])
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-started' } })
      onEvent({ event: 'completed', data: { runId: 'run-started', status: 'COMPLETED', message: '模型已响应', eventCount: 2 } })
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '继续完善' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByText('已经补充完成。')).toBeTruthy()
    await waitFor(() => expect(screen.queryByText('正在思考...')).toBeNull())
  })

  it('keeps a duplicate pending message visible until its own request resolves', async () => {
    const pendingRun = deferred<void>()
    workbenchApiMock.listEvents.mockResolvedValue([
      {
        eventId: 2,
        type: 'USER_MESSAGE',
        createdAt: '2026-07-31T08:01:00Z',
        payloadJson: '{"message":"重复问题"}',
      },
    ])
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-duplicate' } })
      return pendingRun.promise
    })
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '重复问题' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findAllByText('重复问题')).toHaveLength(2)

    pendingRun.resolve()
    await waitFor(() => expect(screen.getAllByText('重复问题')).toHaveLength(1))
  })

  it('shows runtime model configuration state', async () => {
    renderWorkbench()

    await openRecentSession()

    await waitFor(() => expect(workbenchApiMock.getRuntimeConfig).toHaveBeenCalled())
    expect(screen.getAllByText('模型未配置').length).toBeGreaterThan(0)
    expect(screen.getAllByText(/不会调用真实模型/).length).toBeGreaterThan(0)
  })

  it('restores the draft message when sending fails', async () => {
    workbenchApiMock.sendMessageStream.mockRejectedValueOnce(new Error('runtime unavailable'))
    renderWorkbench()

    await openRecentSession()

    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '请完善 README' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await waitFor(() => expect(screen.getByRole('alert').textContent).toContain('runtime unavailable'))
    expect((screen.getByLabelText('给工作台发送消息') as HTMLTextAreaElement).value).toBe('请完善 README')
  })

  it('shows a model failure returned by the runtime without exposing run ids', async () => {
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-failed' } })
      onEvent({ event: 'completed', data: { runId: 'run-failed', status: 'FAILED', message: '模型服务暂时不可用', eventCount: 1 } })
    })
    renderWorkbench()

    await openRecentSession()

    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '请检查技能结构' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    expect(await screen.findByText('模型调用失败')).toBeTruthy()
    expect(screen.getByText('模型服务暂时不可用')).toBeTruthy()
    await waitFor(() => expect(screen.queryByText('run-failed')).toBeNull())
    expect((screen.getByLabelText('给工作台发送消息') as HTMLTextAreaElement).value).toBe('')
  })

  it('allows follow-up messages while a session is running', async () => {
    workbenchApiMock.getSession.mockResolvedValue({
      ...session,
      status: 'RUNNING',
    })
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 可继续')
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '继续补充说明' } })
    const sendButton = screen.getByRole('button', { name: '发送消息' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(false)
    fireEvent.click(sendButton)

    await waitFor(() => expect(workbenchApiMock.sendMessageStream).toHaveBeenCalledWith(7, '继续补充说明', expect.any(Function)))
  })

  it('uses active run from the session response after refresh', async () => {
    const activeRun = {
      runId: 'run-from-server',
      status: 'STARTED',
      message: '模型正在回复',
      eventCount: 0,
      startedAt: new Date().toISOString(),
    } satisfies WorkbenchRuntimeRunResponse
    workbenchApiMock.listSessions.mockResolvedValue([{ ...session, status: 'RUNNING', activeRun }])
    workbenchApiMock.getSession.mockResolvedValue({ ...session, status: 'RUNNING', activeRun })
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 正在回复')
    expect(screen.getByText(/正在理解需求/)).toBeTruthy()
    const sendButton = screen.getByRole('button', { name: '发送消息' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(true)
    fireEvent.click(screen.getByRole('button', { name: '取消生成' }))

    await waitFor(() => expect(workbenchApiMock.cancelRun).toHaveBeenCalledWith(7, 'run-from-server'))
  })

  it('does not keep stale active runs in the replying state', async () => {
    const activeRun = {
      runId: 'stale-run-from-server',
      status: 'STARTED',
      message: '模型正在回复',
      eventCount: 0,
      startedAt: '2026-07-31T00:00:00Z',
    } satisfies WorkbenchRuntimeRunResponse
    workbenchApiMock.listSessions.mockResolvedValue([{ ...session, status: 'RUNNING', activeRun }])
    workbenchApiMock.getSession.mockResolvedValue({ ...session, status: 'RUNNING', activeRun })
    renderWorkbench()

    expect(await screen.findByText('可能中断')).toBeTruthy()
    await openRecentSession()

    await screen.findByText('knowledge-helper · 可继续')
    expect(screen.queryByText('正在回复')).toBeNull()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '继续补充' } })
    const sendButton = screen.getByRole('button', { name: '发送消息' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(false)
  })

  it('makes the injected target context visible in the session header', async () => {
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByText(/knowledge-helper@1\.2\.0 已作为模型上下文注入/)).toBeTruthy()
    expect(screen.getByText(/后续输入是普通对话，不会自动发布/)).toBeTruthy()
  })

  it('renders user and model messages in the conversation panel', async () => {
    workbenchApiMock.listEvents.mockResolvedValue(chatEvents)
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByText('先帮我梳理技能目标')).toBeTruthy()
    expect(screen.getByText('可以，我会先确认目标、输入和输出格式。')).toBeTruthy()
    expect(screen.getByText('你')).toBeTruthy()
    expect(screen.getAllByText('工作台').length).toBeGreaterThan(0)
    expect(screen.queryByText('最终回复')).toBeNull()
  })

  it('renders runtime process events as conversation steps', async () => {
    workbenchApiMock.listEvents.mockResolvedValue([
      ...chatEvents,
      {
        eventId: 4,
        type: 'TOOL_CALL',
        createdAt: '2026-07-31T08:01:02Z',
        payloadJson: JSON.stringify({ toolName: 'write_file' }),
      },
      {
        eventId: 5,
        type: 'FILE_CHANGED',
        createdAt: '2026-07-31T08:01:03Z',
        payloadJson: JSON.stringify({ path: 'SKILL.md' }),
      },
    ])
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByText('工具')).toBeTruthy()
    expect(screen.getByText('调用工具：write_file')).toBeTruthy()
    expect(screen.getAllByText('文件').length).toBeGreaterThan(0)
    expect(screen.getByText('已更新文件：SKILL.md')).toBeTruthy()
  })

  it('renders the newest chat messages when the event window is long', async () => {
    const longEvents: WorkbenchSessionEvent[] = Array.from({ length: 120 }, (_, index) => ({
      eventId: index + 1,
      type: index < 110 ? 'AUDIT' : 'USER_MESSAGE',
      createdAt: `2026-07-31T08:${String(index).padStart(2, '0')}:00Z`,
      payloadJson: JSON.stringify({ message: index < 110 ? `audit ${index}` : `最新消息 ${index}` }),
    }))
    workbenchApiMock.listEvents.mockResolvedValue(longEvents)
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByText('最新消息 119')).toBeTruthy()
    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledWith(7, { limit: 200 }))
  })

  it('exposes files, MCP, approvals, and audit events from the tools tab', async () => {
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(screen.getByRole('tab', { name: '工具' }))
    const toolsPanel = screen.getByRole('tabpanel')

    expect(within(toolsPanel).getByRole('heading', { name: '文件' })).toBeTruthy()
    expect(within(toolsPanel).getByText('SKILL.md')).toBeTruthy()
    expect(within(toolsPanel).getByRole('heading', { name: 'MCP 服务' })).toBeTruthy()
    expect(within(toolsPanel).getByText('MCP 预选会作为后续对话的工具偏好保存到当前会话；运行时调用能力以服务端适配器接入状态为准。')).toBeTruthy()
    expect(within(toolsPanel).getByRole('heading', { name: '待审批工具' })).toBeTruthy()
    fireEvent.click(within(toolsPanel).getAllByText('审计事件')[0])

    expect(within(toolsPanel).getByText('#1')).toBeTruthy()
    const auditDetails = within(toolsPanel).getByText('查看详情').closest('details') as HTMLDetailsElement
    expect(auditDetails.open).toBe(false)
    fireEvent.click(within(toolsPanel).getByText('查看详情'))
    expect(auditDetails.open).toBe(true)
    expect(within(toolsPanel).getByText(/session\.created/)).toBeTruthy()
  })

  it('ignores a runtime result returned after switching sessions', async () => {
    const pendingRun = deferred<void>()
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-1' } })
      return pendingRun.promise
    })
    workbenchApiMock.listSessions.mockResolvedValue([
      session,
      { ...session, id: 8, targetSlug: 'skill-8' },
    ])
    workbenchApiMock.getSession.mockImplementation((sessionId: number) =>
      Promise.resolve({ ...session, id: sessionId, targetSlug: `skill-${sessionId}` }),
    )
    renderWorkbench()

    await openRecentSession()
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '请完善 README' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await openRecentSession(8, /skill-8/)

    pendingRun.resolve()

    await waitFor(() => expect(screen.queryByText('run-1')).toBeNull())
  })

  it('cancels a started runtime run', async () => {
    const pendingRun = deferred<void>()
    workbenchApiMock.sendMessageStream.mockImplementationOnce(async (_sessionId: number, _message: string, onEvent: (event: { event: string; data: Record<string, unknown> }) => void) => {
      onEvent({ event: 'run_started', data: { runId: 'run-1' } })
      return pendingRun.promise
    })
    workbenchApiMock.cancelRun.mockResolvedValue({
      runId: 'run-1',
      status: 'CANCELLED',
      message: '运行任务已取消。',
      eventCount: 1,
    } satisfies WorkbenchRuntimeRunResponse)
    renderWorkbench()

    await openRecentSession()

    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '请完善 README' } })
    fireEvent.click(screen.getByRole('button', { name: '发送消息' }))

    await screen.findByText('正在思考...')
    fireEvent.click(screen.getByRole('button', { name: '取消生成' }))

    await waitFor(() => expect(workbenchApiMock.cancelRun).toHaveBeenCalledWith(7, 'run-1'))
    pendingRun.resolve()
    await waitFor(() => expect(screen.queryByText('正在思考...')).toBeNull())
  })

  it('renders diff status for changed files', async () => {
    renderWorkbench()

    await openRecentSession()
    fireEvent.click(await screen.findByRole('tab', { name: '差异' }))

    const panel = await screen.findByRole('tabpanel')
    expect(within(panel).getByText('SKILL.md')).toBeTruthy()
    expect(within(panel).getByText('已修改')).toBeTruthy()
    expect(within(panel).getByText('references/new.md')).toBeTruthy()
    expect(within(panel).getByText('新增')).toBeTruthy()
  })

  it('shows query failures instead of empty states', async () => {
    workbenchApiMock.listFiles.mockRejectedValue(new Error('文件服务不可用'))
    renderWorkbench()

    await openRecentSession()

    expect(await screen.findByText(/文件列表读取失败/)).toBeTruthy()
    expect(screen.queryByText(/暂无文件/)).toBeNull()
  })

  it('disables mutating actions when the session cannot be loaded', async () => {
    workbenchApiMock.listSessions.mockResolvedValue([{ ...session, id: 404, targetSlug: 'missing-skill' }])
    workbenchApiMock.getSession.mockRejectedValue(new Error('会话不存在'))
    renderWorkbench()

    const missingSession = await screen.findByText('missing-skill')
    fireEvent.click(missingSession.closest('button')!)

    expect(await screen.findByText(/会话读取失败/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText('给工作台发送消息'), { target: { value: '继续生成' } })
    const sendButton = screen.getByRole('button', { name: '发送消息' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(true)
    fireEvent.click(sendButton)
    expect(workbenchApiMock.sendMessage).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('tab', { name: '发布' }))
    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    expect(publishButton.disabled).toBe(true)
    fireEvent.click(publishButton)
    expect(workbenchApiMock.publishPackage).not.toHaveBeenCalled()
  })

  it('disables mutating actions while the session is waiting for approval', async () => {
    workbenchApiMock.getSession.mockResolvedValue({
      ...session,
      status: 'WAITING_APPROVAL',
    })
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 需确认')
    fireEvent.change(await screen.findByLabelText('给工作台发送消息'), { target: { value: '继续生成' } })
    const sendButton = screen.getByRole('button', { name: '发送消息' }) as HTMLButtonElement
    expect(sendButton.disabled).toBe(true)
    fireEvent.click(sendButton)
    expect(workbenchApiMock.sendMessage).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    const saveMcpButton = screen.getByRole('button', { name: '保存预选 MCP' }) as HTMLButtonElement
    expect(saveMcpButton.disabled).toBe(true)
    fireEvent.click(saveMcpButton)
    expect(workbenchApiMock.saveMcpBindings).not.toHaveBeenCalled()

    fireEvent.click(await screen.findByRole('button', { name: /SKILL\.md/ }))
    const saveFileButton = await screen.findByRole('button', { name: '保存文件' }) as HTMLButtonElement
    expect(saveFileButton.disabled).toBe(true)
    fireEvent.click(saveFileButton)
    expect(workbenchApiMock.writeFile).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('tab', { name: '发布' }))
    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    expect(publishButton.disabled).toBe(true)
    fireEvent.click(publishButton)
    expect(workbenchApiMock.publishPackage).not.toHaveBeenCalled()
  })

  it('shows package preview details from the review panel', async () => {
    workbenchApiMock.listApprovals.mockResolvedValue([])
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))

    fireEvent.click(screen.getByRole('button', { name: '生成预览' }))

    await waitFor(() => expect(workbenchApiMock.packagePreview).toHaveBeenCalledWith(7, { visibility: 'PRIVATE' }))
    expect(await screen.findByText('sha256:ready-package')).toBeTruthy()
    expect(screen.getByText('PASSED')).toBeTruthy()
    expect(screen.getByText('manifest ok')).toBeTruthy()
    expect(screen.getByText('policy ok')).toBeTruthy()
    expect(screen.getAllByText('SKILL.md').length).toBeGreaterThan(1)
    expect(screen.getByText('references/guide.md')).toBeTruthy()
    expect(screen.getByText('assets/private.key')).toBeTruthy()
    expect(screen.getByText('unsupported extension')).toBeTruthy()
  })

  it('publishes a ready package preview and displays the published skill result', async () => {
    workbenchApiMock.listApprovals.mockResolvedValue([])
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))

    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    expect(publishButton.disabled).toBe(true)

    fireEvent.click(screen.getByRole('button', { name: '生成预览' }))
    expect(await screen.findByText('sha256:ready-package')).toBeTruthy()
    expect(publishButton.disabled).toBe(false)

    fireEvent.click(publishButton)

    await waitFor(() => expect(workbenchApiMock.publishPackage).toHaveBeenCalledWith(7, {
      confirmPackageFingerprint: 'sha256:ready-package',
      visibility: 'PRIVATE',
    }))
    expect(await screen.findByText('team-ai')).toBeTruthy()
    expect(screen.getAllByText('knowledge-helper').length).toBeGreaterThan(0)
    expect(screen.getByText('1.2.0')).toBeTruthy()
    expect(screen.getByText('PUBLISHED')).toBeTruthy()
  })

  it('clears package preview when visibility changes before publishing', async () => {
    workbenchApiMock.listApprovals.mockResolvedValue([])
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))

    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    fireEvent.click(screen.getByRole('button', { name: '生成预览' }))
    expect(await screen.findByText('sha256:ready-package')).toBeTruthy()
    expect(publishButton.disabled).toBe(false)

    fireEvent.change(screen.getByLabelText('技能包可见性'), { target: { value: 'PUBLIC' } })

    await waitFor(() => expect(screen.queryByText('sha256:ready-package')).toBeNull())
    await waitFor(() => expect((screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement).disabled).toBe(true))
  })

  it('clears package preview when saving workspace files', async () => {
    workbenchApiMock.listApprovals.mockResolvedValue([])
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))

    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    fireEvent.click(screen.getByRole('button', { name: '生成预览' }))
    expect(await screen.findByText('sha256:ready-package')).toBeTruthy()
    expect(publishButton.disabled).toBe(false)

    fireEvent.click(await screen.findByRole('tab', { name: '编辑器' }))
    fireEvent.click(await screen.findByRole('button', { name: /SKILL\.md/ }))
    const editor = await screen.findByLabelText('文件内容')
    fireEvent.change(editor, { target: { value: `${skillFile.content}\n\nUpdated.` } })
    fireEvent.click(screen.getByRole('button', { name: '保存文件' }))

    await waitFor(() => expect(workbenchApiMock.writeFile).toHaveBeenCalled())
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))
    await waitFor(() => expect(screen.queryByText('sha256:ready-package')).toBeNull())
    await waitFor(() => expect((screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement).disabled).toBe(true))
  })

  it('keeps package publishing disabled for not-ready previews', async () => {
    workbenchApiMock.packagePreview.mockResolvedValue({
      ...packagePreview,
      packageFingerprint: '',
      readyToPublish: false,
      validation: {
        status: 'FAILED',
        messages: ['fix package validation first'],
      },
    } satisfies WorkbenchPackagePreview)
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))
    fireEvent.click(screen.getByRole('button', { name: '生成预览' }))

    expect(await screen.findByText('fix package validation first')).toBeTruthy()
    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    expect(publishButton.disabled).toBe(true)
    fireEvent.click(publishButton)
    expect(workbenchApiMock.publishPackage).not.toHaveBeenCalled()
  })

  it('disables package preview and publishing for failed sessions', async () => {
    workbenchApiMock.getSession.mockResolvedValue({
      ...session,
      status: 'FAILED',
    })
    renderWorkbench()

    await openRecentSession()
    await screen.findByText('knowledge-helper · 失败')
    fireEvent.click(await screen.findByRole('tab', { name: '发布' }))

    const previewButton = screen.getByRole('button', { name: '生成预览' }) as HTMLButtonElement
    const publishButton = screen.getByRole('button', { name: '发布技能包' }) as HTMLButtonElement
    expect(previewButton.disabled).toBe(true)
    expect(publishButton.disabled).toBe(true)
    fireEvent.click(previewButton)
    fireEvent.click(publishButton)
    expect(workbenchApiMock.packagePreview).not.toHaveBeenCalled()
    expect(workbenchApiMock.publishPackage).not.toHaveBeenCalled()
  })

  it('renders selectable MCP catalog entries without exposing runtime endpoints', async () => {
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 草稿')
    expect(screen.getAllByText('已预选 MCP').length).toBeGreaterThan(0)
    expect((await screen.findAllByText('Context Tools')).length).toBeGreaterThan(0)
    expect(screen.getByText('MCP 服务')).toBeTruthy()
    expect(screen.queryByText('未预选 MCP 服务；当前预选仅记录会话偏好。')).toBeNull()
    expect(workbenchApiMock.listMcpCatalog).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    expect((await screen.findAllByText('Context Tools')).length).toBeGreaterThan(0)
    expect(screen.getByText('3 个工具')).toBeTruthy()
    expect(screen.getByText('知识库')).toBeTruthy()
    expect((screen.getByRole('checkbox', { name: /选择 Context Tools/ }) as HTMLInputElement).checked).toBe(true)
    expect(screen.queryByText(/internal\.example\.com/)).toBeNull()
    expect(screen.queryByText(/runtimeEndpointRef/)).toBeNull()
  })

  it('shows MCP catalog errors inline without retrying the optional catalog request', async () => {
    workbenchApiMock.listMcpCatalog.mockRejectedValue(new Error('MCP 广场暂时不可用'))

    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 草稿')
    expect(screen.queryByText(/MCP 服务读取失败/)).toBeNull()
    expect(workbenchApiMock.listMcpCatalog).toHaveBeenCalledTimes(1)
    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    expect((await screen.findByRole('alert')).textContent).toContain('MCP 服务读取失败：MCP 广场暂时不可用')
    expect(workbenchApiMock.listMcpCatalog).toHaveBeenCalledTimes(1)
  })

  it('saves selected MCP bindings through the workbench API', async () => {
    renderWorkbench()

    await openRecentSession()

    await screen.findByText('knowledge-helper · 草稿')
    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    expect(await screen.findByText('MCP 预选已保存，将用于后续对话。')).toBeTruthy()
    const ticketTools = await screen.findByRole('checkbox', { name: /选择 Ticket Tools/ })
    fireEvent.click(ticketTools)
    expect(screen.getByText('有未保存的 MCP 预选变更，将从下一轮对话开始生效。')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '保存预选 MCP' }))

    await waitFor(() => expect(workbenchApiMock.saveMcpBindings).toHaveBeenCalledWith(7, {
      serverIds: ['ctx-tools', 'ticket-tools'],
    }))
  })

  it('allows MCP selection changes for a running session and saves them for later turns', async () => {
    workbenchApiMock.getSession.mockResolvedValue({
      ...session,
      status: 'RUNNING',
    })
    renderWorkbench()

    await openRecentSession()

    await screen.findByText(/knowledge-helper@1\.2\.0 已作为模型上下文注入/)
    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    await waitFor(() => expect(workbenchApiMock.listMcpCatalog).toHaveBeenCalled())
    expect(await screen.findByText('MCP 预选已保存，将用于后续对话。')).toBeTruthy()
    const ticketTools = await screen.findByRole('checkbox', { name: /选择 Ticket Tools/ }, { timeout: 5000 }) as HTMLInputElement
    expect(ticketTools.disabled).toBe(false)
    fireEvent.click(ticketTools)
    expect(screen.getByText('有未保存的 MCP 预选变更，将从下一轮对话开始生效。')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: '保存预选 MCP' }))
    await waitFor(() => expect(workbenchApiMock.saveMcpBindings).toHaveBeenCalledWith(7, {
      serverIds: ['ctx-tools', 'ticket-tools'],
    }))
  })

  it('shows the reason when an MCP catalog item cannot be used by the runtime', async () => {
    workbenchApiMock.listMcpCatalog.mockResolvedValue({
      items: [
        {
          ...mcpCatalog[0],
          id: 'disabled-tools',
          name: 'Disabled Tools',
          enabled: false,
          runtimeCandidate: false,
        },
      ],
      total: 1,
      page: 0,
      size: 20,
    })
    renderWorkbench()

    await openRecentSession()

    await screen.findByText(/knowledge-helper@1\.2\.0 已作为模型上下文注入/)
    fireEvent.click(screen.getByRole('button', { name: '预选 MCP 服务' }))
    await waitFor(() => expect(workbenchApiMock.listMcpCatalog).toHaveBeenCalled())
    expect(await screen.findByText('Disabled Tools', {}, { timeout: 5000 })).toBeTruthy()
    expect(screen.getByText('服务已停用，不能作为本次会话工具。')).toBeTruthy()
    expect((screen.getByRole('checkbox', { name: /选择 Disabled Tools/ }) as HTMLInputElement).disabled).toBe(true)
  })

  it('renders pending approval details and refreshes after approve or reject', async () => {
    renderWorkbench()

    await openRecentSession()

    expect(screen.getByText('待审批工具')).toBeTruthy()
    expect(await screen.findByText('update_ticket')).toBeTruthy()
    expect(screen.getByText('需确认')).toBeTruthy()
    expect(screen.getByText('ticket-tools')).toBeTruthy()
    expect(screen.getByText(/"token": "\[REDACTED\]"/)).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '批准' }))
    await waitFor(() => expect(workbenchApiMock.approveApproval).toHaveBeenCalledWith(7, 21))
    await waitFor(() => expect(workbenchApiMock.listApprovals).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(workbenchApiMock.listEvents).toHaveBeenCalledTimes(2))

    fireEvent.click(screen.getByRole('button', { name: '拒绝' }))
    await waitFor(() => expect(workbenchApiMock.rejectApproval).toHaveBeenCalledWith(7, 21))
  })

  it('shows a non-text warning instead of opening binary files for editing', async () => {
    renderWorkbench()

    await openRecentSession()

    fireEvent.click(await screen.findByRole('button', { name: /assets\/logo\.png/ }))

    expect(workbenchApiMock.readFile).not.toHaveBeenCalledWith(7, 'assets/logo.png')
    expect(await screen.findByText('当前文件不是文本文件，Phase 4 仅支持 UTF-8 文本编辑。')).toBeTruthy()
    expect(screen.queryByLabelText('文件内容')).toBeNull()
  })
})





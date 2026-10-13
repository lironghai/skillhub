# E2E Tester Army 接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 SkillHub 前端接入 E2E Tester Army，并用真实本地 Web 应用验证生成示例和一个核心用户流程。

**Architecture:** 在 `skillhub/web` 保留现有 Playwright 配置，同时新增 E2E Tester Army 的独立 `e2e.config.ts`、`tests/` 测试目录和工具依赖。运行器通过配置中的 `pnpm dev` 启动 Vite，使用 `@e2e-dev/web` 驱动 `http://127.0.0.1:3000`。

**Tech Stack:** Node.js、pnpm、TypeScript、E2E Tester Army `e2e` CLI、`@e2e-dev/web`、Vercel AI SDK Gateway、现有 React/Vite 前端。

---

### Task 1: 初始化 E2E Tester Army 并绑定 SkillHub Web

**Files:**
- Create or modify: `skillhub/web/e2e.config.ts` (由初始化器生成后按 SkillHub URL 和启动命令修正)
- Create: `skillhub/web/tests/example.e2e.ts` (由初始化器生成)
- Create or modify: `skillhub/web/.agents/skills/e2e/`, `skillhub/web/.mcp.json` (由初始化器生成)
- Modify: `skillhub/web/package.json`, `skillhub/web/pnpm-lock.yaml`, `skillhub/web/.gitignore` (由初始化器生成)

- [ ] **Step 1: Run the official scaffold without installing dependencies**

Run from `skillhub/web`:

```powershell
pnpm dlx e2e init --yes
```

Expected: the CLI creates the E2E config, example test, skill, MCP config, package dependency declarations, and ignored `.e2e/` output paths without starting the app.

- [ ] **Step 2: Read the generated config and guide output**

Run:

```powershell
pnpm dlx e2e guide setup
pnpm dlx e2e guide writing-tests
pnpm dlx e2e guide running
```

Confirm that the config uses `export default { ... } satisfies E2EConfig`, the target uses `web()`, and the app command is an executable plus argument array.

- [ ] **Step 3: Set the target to SkillHub Web**

Update `skillhub/web/e2e.config.ts` so the target contains:

```ts
{
  engine: web(),
  app: {
    url: 'http://127.0.0.1:3000',
    command: { executable: 'pnpm', args: ['dev'], log: '.e2e/logs/app.log' },
  },
}
```

Keep the generated Gateway model entry until the user selects another provider. Do not add secrets to source files.

- [ ] **Step 4: Ask for the model provider and complete only the required authentication**

Ask the user to choose one of ChatGPT, GitHub Copilot, OpenCode Console, SuperGrok, an API key provider, or a local model. For subscription choices, provide the exact `pnpm dlx e2e login <provider>` equivalent and wait for confirmation before running it. For an API key or local model, document the environment variable or endpoint without writing the value to the repository.

- [ ] **Step 5: Install dependencies and browser runtime**

After the provider decision:

```powershell
pnpm install
pnpm exec e2e-web install chromium
```

Expected: the lockfile is consistent and Chromium is available to the web engine.

- [ ] **Step 6: Run the generated example and repair setup errors**

Run:

```powershell
pnpm exec e2e run tests/example.e2e.ts --workers 1 --retries 0
```

If it fails, inspect `.e2e/results/<test>/trace.md` and `.e2e/logs/app.log`, then repair only the config, dependency, locator, or environment issue identified by the trace. Repeat until the generated test passes.

- [ ] **Step 7: Commit the initialized setup**

```powershell
git add e2e.config.ts tests package.json pnpm-lock.yaml .gitignore .agents .mcp.json
git commit -m "test(web): initialize e2e tester army"
```

### Task 2: Add and verify the highest-value SkillHub flow

**Files:**
- Create: `skillhub/web/tests/public-skill-browse.e2e.ts`
- Modify: `skillhub/web/e2e.config.ts` only if the selected provider or test glob requires it

- [ ] **Step 1: Inspect the existing public-page test and live labels**

Read `skillhub/web/e2e/public-pages.spec.ts`, `skillhub/web/e2e/landing-navigation.spec.ts`, and the corresponding page components under `skillhub/web/src/`. Use the existing route and accessible names rather than inventing selectors.

- [ ] **Step 2: Write one deterministic core-flow test**

Create `tests/public-skill-browse.e2e.ts` using `test` and `expect` from `e2e` and the Web fixtures. The test must open the home route, navigate to the public skill browse route using an accessible link or button, and assert that the destination page exposes its public heading or search control. Use `agent.act` only for a single user-intent step if the selected provider is available; keep URL and visible-control assertions deterministic.

- [ ] **Step 3: Run the focused test**

```powershell
pnpm exec e2e run tests/public-skill-browse.e2e.ts --workers 1 --retries 0
```

Expected: one passing test against the local Vite app. On failure, use the generated trace and adjust the locator or expectation to match the rendered accessible UI; never add a sleep.

- [ ] **Step 4: Re-run both E2E Tester Army tests**

```powershell
pnpm exec e2e run tests/example.e2e.ts tests/public-skill-browse.e2e.ts --workers 1 --retries 0
```

Expected: both tests pass in one run. Record any model, browser, backend, or authentication limitation separately from application failures.

- [ ] **Step 5: Commit the core-flow test**

```powershell
git add tests/public-skill-browse.e2e.ts e2e.config.ts
git commit -m "test(web): cover public skill browsing with e2e agent"
```


# E2E Tester Army

在 `skillhub/web` 中运行：

```powershell
pnpm install --frozen-lockfile
pnpm exec e2e-web install chromium
pnpm test:e2e:agent --workers 1 --retries 0
```

运行器自动启动 `http://127.0.0.1:3000` 的 Vite 开发服务，并在结束后关闭该服务。配置直接用当前 Node 运行 Vite，以兼容 Windows 无法直接启动 `pnpm.cmd` 的限制。端口已占用时先确认占用它的服务，不要停止不相关进程。

- `example.e2e.ts`：官方生成的页面启动示例。
- `public-skill-browse.e2e.ts`：匿名访问首页、搜索关键词、选择下载量排序，以及刷新后恢复搜索状态。

现有 Playwright 仍使用 `pnpm test:e2e`；新增测试不会被 Vitest 收集。

当前配置使用 OpenAI 兼容的本地模型 `deepseek-v4.1-flash`，endpoint 默认是 `http://10.0.13.142:8080/v1`。运行 AI 测试前设置 `E2E_LOCAL_MODEL_API_KEY`；也可以用 `E2E_LOCAL_MODEL_ENDPOINT` 覆盖 endpoint。密钥通过环境变量提供，不写入测试或配置文件。

上述核心流程验证前端交互和 URL 状态，不断言后端搜索结果、技能详情或下载。验证这些行为需要另行启动监听 `8080` 的 SkillHub 后端及其依赖，并准备真实技能数据。

运行输出位于被忽略的 `.e2e/`：`report.json` 是报告，失败详情在 `results/<test>/trace.md`，开发服务日志在 `logs/app.log`。需要保留成功测试的跟踪页时运行：

```powershell
pnpm test:e2e:agent --workers 1 --retries 0 --trace on
```

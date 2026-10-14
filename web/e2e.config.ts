import type { E2EConfig } from 'e2e';
import { web } from '@e2e-dev/web';
import { createOpenAICompatible } from '@ai-sdk/openai-compatible';

const localModel = createOpenAICompatible({
  name: 'skillhub-local',
  baseURL: process.env.E2E_LOCAL_MODEL_ENDPOINT ?? 'http://10.0.13.142:8080/v1',
  apiKey: process.env.E2E_LOCAL_MODEL_API_KEY || undefined,
});

export default {
  assertionTimeout: 30000,
  agents: {
    default: {
      model: localModel('deepseek-v4.1-flash'),
      system: 'You are a careful QA agent. Use only visible controls, complete one goal, and verify the resulting screen.',
    },
  },
  targets: [{
    engine: web(),
    app: {
      url: 'http://127.0.0.1:3000',
      command: {
        // Launch Vite directly: Windows cannot spawn pnpm's .cmd shim without a shell.
        executable: process.execPath,
        args: [
          'node_modules/vite/bin/vite.js',
          '--host',
          '127.0.0.1',
          '--port',
          '3000',
          '--strictPort',
        ],
        startupTimeout: 120000,
        log: '.e2e/logs/app.log',
      },
    },
  }],
} satisfies E2EConfig;

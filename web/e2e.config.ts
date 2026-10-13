import type { E2EConfig } from 'e2e';
import { web } from '@e2e-dev/web';
import { gateway } from 'ai';

export default {
  assertionTimeout: 30000,
  // The Vercel AI Gateway serves the model id and reads AI_GATEWAY_API_KEY, or the OIDC token of a linked Vercel project.
  agents: {
    default: {
      model: gateway('openai/gpt-6-luna-fast'),
      system: 'You are a thorough QA agent. Verify every outcome.',
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

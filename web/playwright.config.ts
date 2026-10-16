import { defineConfig, devices } from '@playwright/test';

const production = process.env.RULECRAFT_E2E_PRODUCTION === 'true';
const desktop = { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } };
const compact = { ...devices['Desktop Chrome'], viewport: { width: 360, height: 740 } };
const featureTests = ['**/deployment-readiness.spec.ts', '**/final-acceptance.spec.ts'];

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.RULECRAFT_E2E_BASE_URL ?? (production ? 'http://127.0.0.1:50113' : 'http://127.0.0.1:50111'),
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: production ? [
    { name: 'desktop-production', testMatch: '**/deployment-readiness.spec.ts', use: desktop },
    { name: 'compact-production', testMatch: '**/deployment-readiness.spec.ts', use: compact },
  ] : [
    { name: 'desktop-chromium', testIgnore: featureTests, use: desktop },
    { name: 'compact-chromium', testIgnore: featureTests, use: compact },
    { name: 'desktop-portfolio', testMatch: '**/final-acceptance.spec.ts', dependencies: ['desktop-chromium', 'compact-chromium'], use: desktop },
    { name: 'compact-portfolio', testMatch: '**/final-acceptance.spec.ts', dependencies: ['desktop-chromium', 'compact-chromium'], use: compact },
  ],
});

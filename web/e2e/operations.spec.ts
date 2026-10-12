import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/'); await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test'); await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });
}

test('verifies and persists tenant operational recovery evidence', async ({ page }, testInfo) => {
  test.setTimeout(180_000); const runtimeErrors: string[] = []; const failedRequests: string[] = [];
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', request => { const failure = request.failure()?.errorText ?? 'unknown'; if (failure !== 'net::ERR_ABORTED') failedRequests.push(`${request.url()} · ${failure}`); });
  await signIn(page); await page.getByRole('button', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Operational posture' })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.operations-heading .operations-status')).toHaveText('READY');
  await expect(page.getByText('V16', { exact: true })).toBeVisible();
  await expect(page.getByText('Authoritative PostgreSQL query succeeded.')).toBeVisible();
  await expect(page.getByText('5 failed attempts / 5 min')).toBeVisible();
  await expect(page.getByText('120 requests / min / key')).toBeVisible();
  const headers = await page.evaluate(async () => Object.fromEntries((await fetch('/api/v1/operations')).headers.entries()));
  expect(headers['cache-control']).toContain('no-store'); expect(headers['referrer-policy']).toBe('no-referrer');
  await page.getByRole('button', { name: 'Run recovery scan' }).click();
  await expect(page.getByText(/Recovery scan completed: 0 matrix runs interrupted/)).toBeVisible({ timeout: 15_000 });
  expect(await page.locator('.recovery-history article').count()).toBeGreaterThan(0);
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  await page.reload(); await expect(page.getByRole('heading', { name: 'Operational posture' })).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.recovery-history article').first()).toBeVisible({ timeout: 15_000 });
  await expect(page.locator('.recovery-history')).toContainText('0 matrices · 0 pointers');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0); expect(runtimeErrors).toEqual([]); expect(failedRequests).toEqual([]);
});

import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible({ timeout: 30_000 });
}

test('audits the persisted decision portfolio and operational boundary', async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', request => { if (request.failure()?.errorText !== 'net::ERR_ABORTED') failedRequests.push(request.url()); });

  await signIn(page);
  await expect(page.locator('.project-card').first()).toBeVisible({ timeout: 30_000 });
  expect(await page.locator('.project-card').count()).toBeGreaterThanOrEqual(10);
  await expect(page.getByText('OWNER', { exact: true }).first()).toBeVisible();
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus-visible')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });

  await page.getByRole('button', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Operational posture', exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('V16', { exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('.operations-heading .operations-status')).toHaveText('READY');
  await expect(page.locator('.recovery-history article').first()).toBeVisible({ timeout: 30_000 });
  const headers = await page.evaluate(async () => Object.fromEntries((await fetch('/api/v1/operations')).headers.entries()));
  expect(headers['cache-control']).toContain('no-store');
  expect(headers['referrer-policy']).toBe('no-referrer');
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Projects', exact: true })).toBeVisible({ timeout: 30_000 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

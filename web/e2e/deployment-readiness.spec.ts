import { expect, test } from '@playwright/test';

test('serves the governed workspace through the private production edge', async ({ page, request }, testInfo) => {
  test.setTimeout(90_000);
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', request => { if (request.failure()?.errorText !== 'net::ERR_ABORTED') failedRequests.push(request.url()); });

  const platformResponse = await page.goto('/');
  expect(platformResponse?.status()).toBe(200);
  expect(platformResponse?.headers()['strict-transport-security']).toContain('max-age=31536000');
  expect(platformResponse?.headers()['x-frame-options']).toBe('DENY');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill(process.env.RULECRAFT_E2E_PASSWORD ?? 'CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });

  await page.getByRole('button', { name: 'Operations' }).click();
  await expect(page.getByRole('heading', { name: 'Operational posture', exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('V16', { exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('.operations-heading .operations-status')).toHaveText('READY');
  const privateRoutes = {
    actuator: (await request.get('/actuator/health')).status(),
    openapi: (await request.get('/v3/api-docs')).status(),
  };
  expect(privateRoutes).toEqual({ actuator: 404, openapi: 404 });
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Operational posture', exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('V16', { exact: true })).toBeVisible({ timeout: 30_000 });
  await expect(page.locator('.operations-heading .operations-status')).toHaveText('READY');
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)).toBeLessThanOrEqual(0);
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

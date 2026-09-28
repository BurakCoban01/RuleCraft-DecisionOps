import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });
}

test('captures, compares, expands, and reloads immutable decision versions', async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', message => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', request => {
    const failure = request.failure()?.errorText ?? 'unknown';
    if (failure !== 'net::ERR_ABORTED') failedRequests.push(`${request.url()} · ${failure}`);
  });

  await signIn(page);
  const suffix = crypto.randomUUID().replaceAll('-', '').slice(0, 8).toUpperCase();
  const projectKey = `V${suffix}`;
  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Project key').fill(projectKey);
  await page.getByLabel('Project name').fill(`Version review ${suffix}`);
  await page.getByRole('button', { name: 'Create project' }).click();
  await page.getByRole('button', { name: new RegExp(projectKey) }).click();
  await page.getByRole('button', { name: 'New decision service' }).click();
  await page.getByLabel('Service key').fill(`VERSION_${suffix}`);
  await page.getByLabel('Name', { exact: true }).fill('Versioned eligibility');
  const inputs = page.getByRole('group', { name: 'Inputs' });
  await inputs.getByLabel('Field key').fill('score');
  await inputs.getByLabel('Label').fill('Credit score');
  await inputs.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button', { name: 'Create decision service' }).click();
  await page.getByRole('article').filter({ hasText: `VERSION_${suffix}` }).getByRole('button', { name: 'Open graph' }).click();

  await page.getByRole('button', { name: 'Initialize decision graph' }).click();
  await page.getByRole('button', { name: 'Add decision' }).click();
  await page.getByLabel('Node key').fill('threshold');
  await page.getByLabel('Decision name').fill('Approval threshold');
  const ports = page.getByRole('group', { name: 'Required inputs' });
  await ports.getByLabel('Port key').fill('score');
  await ports.getByLabel('Label').fill('Score');
  await ports.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button', { name: 'Add decision to canvas' }).click();
  await page.getByLabel('Source output').selectOption({ label: 'Credit score · Credit score · NUMBER' });
  await page.getByLabel('Target input').selectOption({ label: 'Approval threshold · Score · NUMBER' });
  await page.getByRole('button', { name: 'Add connection' }).click();
  await page.getByLabel('Source output').selectOption({ label: 'Approval threshold · Result · BOOLEAN' });
  await page.getByLabel('Target input').selectOption({ label: 'Result · Result · BOOLEAN' });
  await page.getByRole('button', { name: 'Add connection' }).click();
  await page.getByRole('button', { name: 'Save graph' }).click();
  await expect(page.getByText('Graph saved and validated.')).toBeVisible();
  await page.getByRole('article').filter({ hasText: 'Approval threshold' }).getByRole('button', { name: 'Expression' }).click();
  await page.getByRole('button', { name: 'Initialize expression' }).click();
  await page.getByLabel('Expression source').fill('score >= 700');
  await page.getByRole('button', { name: 'Save expression' }).click();
  await expect(page.getByText('Expression saved and validated.')).toBeVisible();

  await page.getByRole('button', { name: 'Back to decision graph' }).click();
  await page.getByRole('button', { name: 'Versions' }).click();
  await expect(page.getByRole('heading', { name: 'Version diff' })).toBeVisible();
  await page.getByRole('button', { name: 'Capture baseline' }).click();
  await page.getByLabel('Version label').fill('Approved baseline');
  await page.getByLabel('Review note').fill('Initial threshold for reviewer comparison');
  await page.getByRole('button', { name: 'Capture working decision' }).click();
  await expect(page.getByText('Version 1 captured as valid.')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('V1', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Back to decision graph' }).click();
  await page.getByRole('article').filter({ hasText: 'Approval threshold' }).getByRole('button', { name: /Expression/ }).click();
  await page.getByLabel('Expression source').fill('score >= 650');
  await page.getByRole('button', { name: 'Save expression' }).click();
  await expect(page.getByText('Expression saved and validated.')).toBeVisible();
  await page.getByRole('button', { name: 'Back to decision graph' }).click();
  await page.getByRole('button', { name: 'Versions' }).click();
  await page.getByRole('button', { name: 'Capture version' }).click();
  await page.getByLabel('Version label').fill('Lower threshold');
  await page.getByLabel('Review note').fill('Candidate lowers the approval threshold to 650');
  await page.getByRole('button', { name: 'Capture working decision' }).click();
  await expect(page.getByText('Version 2 captured as valid.')).toBeVisible({ timeout: 15_000 });
  await page.getByRole('button', { name: 'Compare versions' }).click();
  await expect(page.getByRole('heading', { name: 'Change review' })).toBeVisible();
  await expect(page.getByText('IMPLEMENTATION · CHANGED')).toBeVisible();
  await page.getByRole('button', { name: /Review values/ }).click();
  await expect(page.getByText(/score >= 700/)).toBeVisible();
  await expect(page.getByText(/score >= 650/)).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Version diff' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('heading', { name: 'Captured versions' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Approved baseline' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Lower threshold' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

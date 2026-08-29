import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 15_000 });
}

test('authors a reusable enum and a typed decision-service contract', async ({ page }, testInfo) => {
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  await signIn(page);

  const suffix = Date.now().toString().slice(-7);
  const projectKey = `C${suffix}`;
  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Project key').fill(projectKey);
  await page.getByLabel('Project name').fill(`Eligibility ${suffix}`);
  await page.getByRole('button', { name: 'Create project' }).click();
  const projectCard = page.getByRole('button', { name: new RegExp(projectKey) });
  await projectCard.click();
  await expect(page.getByRole('heading', { name: 'No decision services yet' })).toBeVisible();

  await page.getByRole('button', { name: /^Types/ }).click();
  await page.getByRole('button', { name: 'New type' }).click();
  await page.getByLabel('Type key').fill(`RISK_${suffix}`);
  await page.getByLabel('Name', { exact: true }).fill('Risk band');
  await page.getByLabel('Kind').selectOption('ENUM');
  await page.getByLabel('Stored value').fill('LOW');
  await page.getByLabel('Display label').fill('Low risk');
  await page.getByRole('button', { name: 'Create type' }).click();
  await expect(page.getByRole('heading', { name: 'Risk band' })).toBeVisible();

  await page.getByRole('button', { name: /^Decision services/ }).click();
  await page.getByRole('button', { name: 'New decision service' }).click();
  await page.getByLabel('Service key').fill(`ELIG_${suffix}`);
  await page.getByLabel('Name', { exact: true }).fill('Eligibility decision');
  const inputs = page.getByRole('group', { name: 'Inputs' });
  await inputs.getByLabel('Field key').fill('riskBand');
  await inputs.getByLabel('Label').fill('Risk band');
  await inputs.getByLabel('Type').selectOption({ label: `Risk band · RISK_${suffix}` });
  await page.getByRole('button', { name: 'Create decision service' }).click();
  const service = page.getByRole('article').filter({ hasText: `ELIG_${suffix}` });
  await expect(service.getByRole('heading', { name: 'Eligibility decision' })).toBeVisible();
  await expect(service.getByText('1 inputs')).toBeVisible();
  await service.getByRole('button', { name: 'Archive' }).click();
  await expect(service.getByText('ARCHIVED', { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: `Eligibility ${suffix}` })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('heading', { name: 'Eligibility decision' })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

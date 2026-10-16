import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });
}

test('authors and persists a valid typed decision graph', async ({ page }, testInfo) => {
  const runtimeErrors: string[] = []; const failedRequests: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', (request) => { const failure = request.failure()?.errorText ?? 'unknown'; if (failure !== 'net::ERR_ABORTED') failedRequests.push(`${request.url()} · ${failure}`); });
  await signIn(page);
  const suffix = Date.now().toString().slice(-7); const projectKey = `G${suffix}`;
  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Project key').fill(projectKey);
  await page.getByLabel('Project name').fill(`Graph decisions ${suffix}`);
  await page.getByRole('button', { name: 'Create project' }).click();
  await page.getByRole('button', { name: new RegExp(projectKey) }).click();
  await page.getByRole('button', { name: 'New decision service' }).click();
  await page.getByLabel('Service key').fill(`ELIG_${suffix}`);
  await page.getByLabel('Name', { exact: true }).fill('Eligibility graph');
  const inputs = page.getByRole('group', { name: 'Inputs' });
  await inputs.getByLabel('Field key').fill('score');
  await inputs.getByLabel('Label').fill('Applicant score');
  await inputs.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button', { name: 'Create decision service' }).click();
  const service = page.getByRole('article').filter({ hasText: `ELIG_${suffix}` });
  await service.getByRole('button', { name: 'Open graph' }).click();
  await expect(page.getByRole('heading', { name: 'No graph has been initialized' })).toBeVisible();
  await page.getByRole('button', { name: 'Initialize decision graph' }).click();
  await page.getByRole('button', { name: 'Add decision' }).click();
  await page.getByLabel('Node key').fill('eligibilityDecision');
  await page.getByLabel('Decision name').fill('Eligibility decision');
  const decisionInputs = page.getByRole('group', { name: 'Required inputs' });
  await decisionInputs.getByLabel('Port key').fill('score');
  await decisionInputs.getByLabel('Label').fill('Score');
  await decisionInputs.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button', { name: 'Add decision to canvas' }).click();
  await page.getByLabel('Source output').selectOption({ label: 'Applicant score · Applicant score · NUMBER' });
  await page.getByLabel('Target input').selectOption({ label: 'Eligibility decision · Score · NUMBER' });
  await page.getByRole('button', { name: 'Add connection' }).click();
  await page.getByLabel('Source output').selectOption({ label: 'Eligibility decision · Result · BOOLEAN' });
  await page.getByLabel('Target input').selectOption({ label: 'Result · Result · BOOLEAN' });
  await page.getByRole('button', { name: 'Add connection' }).click();
  await page.getByRole('button', { name: 'Save graph' }).click();
  await expect(page.getByText('Graph saved and validated.')).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('VALID', { exact: true })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Eligibility graph' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByText('Graph is structurally ready')).toBeVisible();
  const nodeFooter = page.locator('.graph-node.decision > footer');
  await expect(nodeFooter).toBeVisible();
  expect(await nodeFooter.evaluate((footer) => {
    const bounds = footer.getBoundingClientRect();
    return [...footer.querySelectorAll('button')].every((button) => {
      const rect = button.getBoundingClientRect();
      return rect.left >= bounds.left && rect.right <= bounds.right
        && rect.top >= bounds.top && rect.bottom <= bounds.bottom;
    });
  })).toBe(true);
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  expect(runtimeErrors).toEqual([]); expect(failedRequests).toEqual([]);
});

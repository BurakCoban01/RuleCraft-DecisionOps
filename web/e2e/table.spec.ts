import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });
}

test('authors and reloads a typed FIRST decision table', async ({ page }, testInfo) => {
  test.setTimeout(60_000);
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', (request) => {
    const failure = request.failure()?.errorText ?? 'unknown';
    if (failure !== 'net::ERR_ABORTED') failedRequests.push(`${request.url()} · ${failure}`);
  });

  await signIn(page);
  const suffix = Date.now().toString().slice(-7);
  const projectKey = `T${suffix}`;
  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Project key').fill(projectKey);
  await page.getByLabel('Project name').fill(`Table decisions ${suffix}`);
  await page.getByRole('button', { name: 'Create project' }).click();
  await page.getByRole('button', { name: new RegExp(projectKey) }).click();

  await page.getByRole('button', { name: 'New decision service' }).click();
  await page.getByLabel('Service key').fill(`ELIG_${suffix}`);
  await page.getByLabel('Name', { exact: true }).fill('Eligibility table');
  const inputs = page.getByRole('group', { name: 'Inputs' });
  await inputs.getByLabel('Field key').fill('score');
  await inputs.getByLabel('Label').fill('Applicant score');
  await inputs.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button', { name: 'Create decision service' }).click();
  await page.getByRole('article').filter({ hasText: `ELIG_${suffix}` }).getByRole('button', { name: 'Open graph' }).click();

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
  await expect(page.getByText('Graph saved and validated.')).toBeVisible();

  const decision = page.getByRole('article').filter({ hasText: 'Eligibility decision' });
  await decision.getByRole('button', { name: 'Table' }).click();
  await page.getByRole('button', { name: 'Initialize decision table' }).click();
  await page.getByRole('button', { name: 'Add rule' }).click();
  await page.getByLabel('Rule 1 Score operator').selectOption('EQUALS');
  await page.getByLabel('Rule 1 Score value').fill('700');
  await page.getByLabel('Rule 1 Result result').fill('true');
  await page.getByLabel('Rule 1 description').fill('Approve strong applicants');
  await page.getByRole('button', { name: 'Add rule' }).click();
  await page.getByLabel('Rule 2 Result result').fill('false');
  await page.getByLabel('Rule 2 description').fill('Default decline');
  await page.getByLabel('Hit policy').selectOption('FIRST');
  await page.getByRole('button', { name: 'Save table' }).click();
  await expect(page.getByText('Decision table saved and validated.')).toBeVisible();
  await expect(page.getByText('VALID', { exact: true })).toBeVisible();

  await page.reload();
  await expect(page.getByRole('heading', { name: 'Eligibility decision' })).toBeVisible({ timeout: 15_000 });
  await expect(page.getByLabel('Hit policy')).toHaveValue('FIRST');
  await expect(page.getByLabel('Rule 1 Score value')).toHaveValue('700');
  await expect(page.getByLabel('Rule 2 Result result')).toHaveValue('false');
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 15_000 });
}

test('signs in, creates a governed project, and exposes administrator navigation', async ({ page }, testInfo) => {
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', (message) => { if (message.type() === 'error') runtimeErrors.push(message.text()); });
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  await signIn(page);
  await expect(page.locator('button:visible', { hasText: 'Team access' })).toBeVisible();

  const key = `P${Date.now().toString().slice(-8)}`;
  await page.getByRole('button', { name: 'New project' }).click();
  await page.getByLabel('Project key').fill(key);
  await page.getByLabel('Project name').fill('Pricing governance');
  await page.getByLabel('Description').fill('Decision ownership, review, and controlled change.');
  await page.getByRole('button', { name: 'Create project' }).click();
  const createdCard = page.getByRole('button', { name: new RegExp(key) });
  await expect(createdCard.getByRole('heading', { name: 'Pricing governance' })).toBeVisible();
  await expect(createdCard.getByText('OWNER', { exact: true })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus-visible')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('workspace.png'), fullPage: true });
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

test('persists the dark theme across reload before authentication', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Use dark theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await expect(page.getByRole('heading', { name: 'Sign in to RuleCraft' })).toBeVisible();
});

test('recovers from a platform failure and returns to private sign-in', async ({ page }) => {
  let unavailable = true;
  await page.route('**/api/v1/platform', async (route) => {
    if (unavailable) await route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{}' });
    else await route.continue();
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'The decision workspace cannot be reached' })).toBeVisible();
  unavailable = false;
  await page.getByRole('button', { name: 'Try connection again' }).click();
  await expect(page.getByRole('heading', { name: 'Sign in to RuleCraft' })).toBeVisible();
});

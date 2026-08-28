import { expect, test } from '@playwright/test';

test('renders the connected workspace without horizontal overflow', async ({ page }) => {
  const runtimeErrors: string[] = [];
  const failedRequests: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') runtimeErrors.push(message.text());
  });
  page.on('requestfailed', (request) => failedRequests.push(request.url()));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Decision workspace' })).toBeVisible();
  await expect(page.getByText('Connected', { exact: true })).toBeVisible();
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  expect(overflow).toBeLessThanOrEqual(0);
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus-visible')).toBeVisible();
  expect(runtimeErrors).toEqual([]);
  expect(failedRequests).toEqual([]);
});

test('persists the dark theme across reload', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Use dark theme' }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');
});

test('recovers from a service failure without losing workspace context', async ({ page }) => {
  let unavailable = true;
  await page.route('**/api/v1/platform', async (route) => {
    if (unavailable) {
      await route.fulfill({ status: 503, contentType: 'application/problem+json', body: '{}' });
    } else {
      await route.continue();
    }
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'The decision workspace cannot be reached' })).toBeVisible();
  unavailable = false;
  await page.getByRole('button', { name: 'Try connection again' }).click();
  await expect(page.getByRole('heading', { name: 'Decision workspace' })).toBeVisible();
});

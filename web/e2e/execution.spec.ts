import { expect, test, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Workspace').fill('acme-rules');
  await page.getByLabel('Email').fill('admin@example.test');
  await page.getByLabel('Password').fill('CorrectHorseBattery12!');
  await page.getByRole('button', { name: 'Continue to workspace' }).click();
  await expect(page.getByRole('heading', { name: 'Projects' })).toBeVisible({ timeout: 30_000 });
}

test('evaluates a saved typed decision and reloads the sandbox route', async ({ page }, testInfo) => {
  test.setTimeout(70_000);
  const runtimeErrors: string[] = [];const failedRequests: string[] = [];
  page.on('console',(message)=>{if(message.type()==='error')runtimeErrors.push(message.text());});
  page.on('requestfailed',(request)=>{const failure=request.failure()?.errorText??'unknown';if(failure!=='net::ERR_ABORTED')failedRequests.push(`${request.url()} · ${failure}`);});
  await signIn(page);
  const suffix=Date.now().toString().slice(-7);const projectKey=`R${suffix}`;
  await page.getByRole('button',{name:'New project'}).click();
  await page.getByLabel('Project key').fill(projectKey);await page.getByLabel('Project name').fill(`Runtime decisions ${suffix}`);await page.getByRole('button',{name:'Create project'}).click();
  await page.getByRole('button',{name:new RegExp(projectKey)}).click();await page.getByRole('button',{name:'New decision service'}).click();
  await page.getByLabel('Service key').fill(`RUN_${suffix}`);await page.getByLabel('Name',{exact:true}).fill('Runtime eligibility');
  const inputs=page.getByRole('group',{name:'Inputs'});await inputs.getByLabel('Field key').fill('score');await inputs.getByLabel('Label').fill('Credit score');await inputs.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button',{name:'Create decision service'}).click();await page.getByRole('article').filter({hasText:`RUN_${suffix}`}).getByRole('button',{name:'Open graph'}).click();
  await page.getByRole('button',{name:'Initialize decision graph'}).click();await page.getByRole('button',{name:'Add decision'}).click();
  await page.getByLabel('Node key').fill('threshold');await page.getByLabel('Decision name').fill('Approval threshold');
  const ports=page.getByRole('group',{name:'Required inputs'});await ports.getByLabel('Port key').fill('score');await ports.getByLabel('Label').fill('Score');await ports.getByLabel('Type').selectOption('builtin:NUMBER');
  await page.getByRole('button',{name:'Add decision to canvas'}).click();
  await page.getByLabel('Source output').selectOption({label:'Credit score · Credit score · NUMBER'});await page.getByLabel('Target input').selectOption({label:'Approval threshold · Score · NUMBER'});await page.getByRole('button',{name:'Add connection'}).click();
  await page.getByLabel('Source output').selectOption({label:'Approval threshold · Result · BOOLEAN'});await page.getByLabel('Target input').selectOption({label:'Result · Result · BOOLEAN'});await page.getByRole('button',{name:'Add connection'}).click();
  await page.getByRole('button',{name:'Save graph'}).click();await expect(page.getByText('Graph saved and validated.')).toBeVisible();
  await page.getByRole('article').filter({hasText:'Approval threshold'}).getByRole('button',{name:'Expression'}).click();await page.getByRole('button',{name:'Initialize expression'}).click();await page.getByLabel('Expression source').fill('score >= 700');await page.getByRole('button',{name:'Save expression'}).click();await expect(page.getByText('Expression saved and validated.')).toBeVisible();
  await page.getByRole('button',{name:'Back to decision graph'}).click();await page.getByRole('button',{name:'Evaluate'}).click();
  await page.getByLabel(/Credit score/).fill('750');await page.getByRole('button',{name:'Evaluate saved graph'}).click();
  await expect(page.locator('.output-grid pre').filter({hasText:'true'})).toBeVisible();await expect(page.getByText('Approval threshold')).toBeVisible();await expect(page.getByText('EXPRESSION')).toBeVisible();await expect(page.getByText('v1',{exact:true})).toBeVisible();
  await page.screenshot({path:testInfo.outputPath('workspace.png'),fullPage:true});
  await page.reload();await expect(page.getByRole('heading',{name:'Evaluate a decision'})).toBeVisible({timeout:15_000});await expect(page.getByText('No evaluation yet')).toBeVisible();
  const overflow=await page.evaluate(()=>document.documentElement.scrollWidth-document.documentElement.clientWidth);expect(overflow).toBeLessThanOrEqual(0);
  expect(runtimeErrors).toEqual([]);expect(failedRequests).toEqual([]);
});

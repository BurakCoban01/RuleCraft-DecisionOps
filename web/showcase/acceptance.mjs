import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { chromium } from '@playwright/test';
import { LocalClient, localOrigin } from './client.mjs';
import { projectKey, scenarios, serviceKey } from './scenario.mjs';

const origin = localOrigin(process.env.RULECRAFT_SHOWCASE_ORIGIN ?? 'http://127.0.0.1:50111');
const client = await LocalClient.signIn(origin, 'northstar-rules', process.env.RULECRAFT_SHOWCASE_EMAIL, process.env.RULECRAFT_SHOWCASE_PASSWORD);
const browser = await chromium.launch();
const context = await browser.newContext({ storageState: await client.context.storageState(), viewport: { width: 1600, height: 1000 }, reducedMotion: 'reduce' });
const page = await context.newPage();
const errors = [];
page.on('pageerror', error => errors.push(error.message));
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()); });
const evidence = { synthetic: true, routes: [], responsive: [], errors };
async function visit(route, selector) {
  await page.goto(`${origin}${route}`);
  await page.locator(selector).first().waitFor();
  await page.waitForLoadState('networkidle');
  assert.equal(await page.locator('[role="alert"]:visible').count(), 0, route);
  evidence.routes.push(route);
}
async function noOverflow(label) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
  assert(overflow <= 0, `${label}: page overflow ${overflow}px`);
}
try {
  const projects = await client.call('/api/v1/projects');
  assert.equal(projects.length, 3);
  const project = projects.find(item => item.projectKey === projectKey);
  for (const supporting of projects.filter(item => item.projectKey !== projectKey)) {
    const catalog = await client.call(`/api/v1/projects/${supporting.id}/decision-services`);
    assert.equal(catalog.length, 1);
    const model = await client.call(`/api/v1/projects/${supporting.id}/decision-services/${catalog[0].id}/graph`);
    assert.equal(model.nodes.length, 5); assert.equal(model.validationStatus, 'VALID');
  }
  const root = `/api/v1/projects/${project.id}`;
  const services = await client.call(`${root}/decision-services`);
  const service = services.find(item => item.serviceKey === serviceKey);
  const apiRoot = `${root}/decision-services/${service.id}`;
  const graphPath = `/projects/${project.id}/services/${service.id}/graph`;
  const graph = await client.call(`${apiRoot}/graph`);
  assert.equal(graph.nodes.length, 9); assert.equal(graph.edges.length, 12); assert.equal(graph.validationStatus, 'VALID');
  assert.equal((await client.call(`${root}/types`)).length, 9); assert.equal(services.length, 4);
  const matrices = await client.call(`${apiRoot}/regression-matrices`);
  assert.equal(matrices.length, 3);
  matrices.forEach(item => { assert.equal(item.status, 'COMPLETED'); assert.equal(item.passedCount, 18); assert.equal(item.failedCount + item.errorCount, 0); });
  const versions = await client.call(`${apiRoot}/versions`);
  assert.equal(versions.length, 3); assert.equal(new Set(versions.map(item => item.fingerprint)).size, 3);
  const workflow = await client.call(`${apiRoot}/change-requests`);
  assert.equal(workflow.requests.length, 3);
  for (const item of workflow.requests) {
    assert.equal(item.state, 'PROMOTED');
    const detail = await client.call(`${apiRoot}/change-requests/${item.id}`);
    assert.deepEqual(detail.events.map(event => event.eventType), ['CREATED', 'SUBMITTED', 'APPROVED', 'PROMOTED']);
    assert.deepEqual(detail.events.map(event => event.actorName), ['Elif Author', 'Elif Author', 'Mert Reviewer', 'Ada Admin']);
  }
  const runtime = await client.call(`${apiRoot}/runtime-sandbox`);
  assert.equal(runtime.deployments.length, 4);
  assert(runtime.deployments.some(item => item.state === 'ROLLED_BACK'));
  assert.equal(runtime.deployments.find(item => item.id === runtime.environment.activeDeploymentId).decisionVersionNumber, 3);
  const working = await client.call(`${apiRoot}/versions/working`);
  assert.equal(working.fingerprint, versions[0].fingerprint);

  await visit(`/projects/${project.id}`, '.catalog-row');
  assert.equal(await page.locator('.catalog-row').count(), 4);
  await page.getByRole('button', { name: /^Types/ }).click();
  assert.equal(await page.locator('.type-card').count(), 9);
  await page.getByRole('button', { name: /^Access/ }).click();
  await page.getByRole('heading', { name: 'Project members', exact: true }).waitFor();
  assert.equal(await page.locator('.data-table [role="row"]').count(), 5);
  await visit('/admin/users', '.users-table');
  assert.equal(await page.locator('.users-table .table-row').count(), 5);

  await visit(graphPath, '.graph-node');
  await page.getByRole('button', { name: 'Hide inspector', exact: true }).click();
  await page.getByRole('button', { name: 'Fit to content', exact: true }).click();
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const geometry = await page.evaluate(({ edges, modelNodes }) => {
    const canvas = document.querySelector('.graph-canvas-shell').getBoundingClientRect();
    const nodes = [...document.querySelectorAll('.graph-node')].map(element => element.getBoundingClientRect());
    const contained = nodes.every(node => node.left >= canvas.left && node.right <= canvas.right && node.top >= canvas.top && node.bottom <= canvas.bottom);
    const intersections = [];
    const distances = edges.map(edge => {
      const path = document.querySelector(`[data-edge-id="${edge.id}"]`);
      const ctm = path.getScreenCTM();
      const from = path.getPointAtLength(0).matrixTransform(ctm);
      const to = path.getPointAtLength(path.getTotalLength()).matrixTransform(ctm);
      const source = document.querySelector(`[data-port-id="${edge.sourcePortId}"] i`).getBoundingClientRect();
      const target = document.querySelector(`[data-port-id="${edge.targetPortId}"] i`).getBoundingClientRect();
      const owners = modelNodes.filter(node => node.ports.some(port => port.id === edge.sourcePortId || port.id === edge.targetPortId)).map(node => node.id);
      for (const element of document.querySelectorAll('.graph-node')) {
        if (owners.includes(element.dataset.nodeId)) continue;
        const box = element.getBoundingClientRect();
        for (let sample = 1; sample < 50; sample++) {
          const point = path.getPointAtLength(path.getTotalLength() * sample / 50).matrixTransform(ctm);
          if (point.x > box.left + 2 && point.x < box.right - 2 && point.y > box.top + 2 && point.y < box.bottom - 2) {
            intersections.push({ edgeId: edge.id, nodeId: element.dataset.nodeId }); break;
          }
        }
      }
      return Math.max(Math.hypot(from.x - (source.left + source.width / 2), from.y - (source.top + source.height / 2)), Math.hypot(to.x - (target.left + target.width / 2), to.y - (target.top + target.height / 2)));
    });
    return { contained, distances, intersections, canvasBottom: canvas.bottom, viewportHeight: innerHeight };
  }, { edges: graph.edges, modelNodes: graph.nodes });
  assert(geometry.contained, 'Fit must contain every node.');
  assert(geometry.canvasBottom <= geometry.viewportHeight, 'Graph must fit the desktop workbench height.');
  assert(geometry.distances.every(distance => distance < 3), `Edges must meet real typed ports: ${geometry.distances}`);
  assert.deepEqual(geometry.intersections, [], 'Dependency paths must not cross unrelated node cards.');
  evidence.graph = geometry;
  await page.getByRole('button', { name: 'Actual size', exact: true }).click();
  assert.equal(await page.getByLabel('Graph zoom').textContent(), '100%');
  await page.getByRole('button', { name: 'Zoom out', exact: true }).click();
  assert.equal(await page.getByLabel('Graph zoom').textContent(), '90%');
  await page.getByRole('button', { name: 'Fit to content', exact: true }).click();
  await page.getByRole('button', { name: 'Inspect connections', exact: true }).focus();
  await page.keyboard.press('Enter');
  assert.equal(await page.getByLabel('Source output').isVisible(), true);
  await page.getByRole('button', { name: 'Hide inspector', exact: true }).click();

  const policy = graph.nodes.find(item => item.nodeKey === 'policy');
  await visit(`${graphPath}/nodes/${policy.id}/table`, '.decision-table');
  await page.getByRole('button', { name: 'Review policy', exact: true }).click();
  assert.equal(await page.locator('.policy-review tbody tr').count(), 10);
  assert.equal(await page.getByText('Approve prime-risk applicants within limits', { exact: true }).isVisible(), true);
  await noOverflow('policy review');
  await page.getByRole('button', { name: 'Edit policy', exact: true }).click();
  assert.equal(await page.getByLabel('Rule 1 Eligible value', { exact: true }).inputValue(), 'false');

  const expression = graph.nodes.find(item => item.nodeKey === 'affordability');
  await visit(`${graphPath}/nodes/${expression.id}/expression`, '.expression-diagnostics');
  assert((await page.getByLabel('Expression source').inputValue()).includes('<= 0.40'));
  assert.equal(await page.getByRole('heading', { name: 'Expression is type-safe', exact: true }).isVisible(), true);
  await visit(`${graphPath}/sandbox`, '.sandbox-inputs');
  await page.getByLabel(/Loan application/).fill(JSON.stringify(scenarios[0].inputs.application));
  await page.getByRole('button', { name: 'Evaluate saved graph', exact: true }).click();
  await page.locator('.decision-path li').first().waitFor();
  assert.equal(await page.locator('.decision-path li').count(), 6);
  assert.equal(await page.getByText('APPROVE', { exact: true }).count() > 0, true);
  await visit(`${graphPath}/tests`, '.scenario-row[role="row"]');
  assert.equal(await page.locator('.scenario-grid article').count(), 18);
  await page.getByLabel('Search scenarios').fill('fraud');
  assert.equal(await page.locator('.scenario-grid article').count(), 1);
  await visit(`${graphPath}/regression`, '.matrix-history-list button');
  await page.locator('.matrix-history-list button').first().click();
  await page.locator('.matrix-result').first().waitFor();
  assert.equal(await page.locator('.matrix-result.passed').count(), 18);
  await page.getByRole('button', { name: /Inspect evidence/ }).first().click();
  assert.equal(await page.locator('.comparison-row').count(), 2);
  await visit(`${graphPath}/versions`, '.version-history');
  await page.getByRole('button', { name: 'Compare versions', exact: true }).click();
  await page.locator('.semantic-change').first().waitFor();
  assert.equal(await page.locator('.semantic-change').count(), 4);
  await page.getByRole('button', { name: 'Approval & promotion', exact: true }).click();
  await page.locator('.workflow-timeline article').first().waitFor();
  assert.equal(await page.locator('.workflow-timeline article').count(), 4);
  await page.reload();
  await page.getByRole('heading', { name: 'Approval & promotion', exact: true }).waitFor();
  await visit(`${graphPath}/runtime`, '.trace-steps li');
  assert.equal(await page.locator('.trace-steps li').count(), 6);
  assert.equal(await page.locator('.trace-redacted').count(), 3);
  const credentials = await client.call(`${apiRoot}/api-credentials`);
  assert.equal(credentials.length, 1); assert(credentials.every(item => item.status === 'REVOKED'));
  assert.equal(await page.locator('.api-secret:visible').count(), 0);
  await page.locator('.trace-explorer').evaluate(element => element.scrollIntoView({ block: 'start' }));
  await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  const rail = await page.locator('.navigation-rail').boundingBox();
  assert.equal(rail.y, 0, 'Navigation must remain present when reading long evidence.');
  const brand = await page.locator('.navigation-rail .brand').boundingBox();
  assert(brand.y >= 0 && brand.y + brand.height <= 1000, 'Product identity remains visible after scrolling.');
  await visit(`${graphPath}/analytics`, '.analytics-kpis');
  assert.equal(await page.getByText('61', { exact: true }).count() > 0, true);
  await visit(`${graphPath}/impact`, '.impact-scorecard');
  assert.equal(await page.getByText('47', { exact: true }).count() > 0, true);
  await visit('/admin/operations', '.recovery-history article');
  assert.equal(await page.locator('.operations-heading .operations-status').textContent(), 'READY');

  for (const width of [360, 600, 760, 761, 1024, 1440, 1600]) {
    await page.setViewportSize({ width, height: width < 761 ? 740 : 1000 });
    for (const [suffix, selector] of [['', '.graph-node'], [`/nodes/${policy.id}/table`, '.decision-table'], ['/regression', '.matrix-case-list'], ['/versions#approval', '.promotion-banner'], ['/runtime', '.trace-steps li'], ['/analytics', '.analytics-kpis']]) {
      await visit(`${graphPath}${suffix}`, selector);
      if (width === 360 && suffix === '') assert.equal(await page.getByLabel('Graph zoom').textContent(), '100%', 'Compact authoring starts at readable actual size; overview is opt-in.');
      await noOverflow(`${width}px ${suffix}`);
    }
    evidence.responsive.push({ width, passed: true });
  }
  await page.setViewportSize({ width: 360, height: 740 });
  await page.getByRole('button', { name: 'Use dark theme', exact: true }).click();
  assert.equal(await page.locator('html').getAttribute('data-theme'), 'dark');
  await visit(`${graphPath}/nodes/${policy.id}/table`, '.decision-table');
  await noOverflow('compact dark table');
  await page.getByRole('button', { name: 'Review policy', exact: true }).click();
  assert.equal(await page.locator('.policy-review tbody tr').count(), 10);
  await page.getByRole('button', { name: 'Use light theme', exact: true }).click();
  assert.deepEqual(errors, []);
  assert.equal((await client.call(`${apiRoot}/graph`)).version, graph.version, 'Read-only inspection and viewport controls must not mutate the graph.');
  evidence.passed = true;
  if (process.env.RULECRAFT_SHOWCASE_ACCEPTANCE_REPORT) await writeFile(process.env.RULECRAFT_SHOWCASE_ACCEPTANCE_REPORT, JSON.stringify(evidence, null, 2));
  console.log('Showcase acceptance passed: persisted workflow, typed-edge geometry, all workbenches, keyboard controls, light/dark and seven responsive widths.');
} finally { await context.close(); await browser.close(); await client.close(); }

import assert from 'node:assert/strict';
import { writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { LocalClient, localOrigin, requestId } from './client.mjs';
import { expressions, policyRules, policyVersions, projectKey, scenarios, serviceKey } from './scenario.mjs';

const builtin = name => ({ builtinType: name, customTypeId: null });
const custom = type => ({ builtinType: null, customTypeId: type.id });
const field = (fieldKey, label, type) => ({ fieldKey, label, type, required: true });

export async function populate({ origin, workspace, email, password }) {
  localOrigin(origin);
  assert(workspace === 'northstar-rules', 'Use the dedicated northstar-rules local workspace.');
  assert(email.endsWith('.test') && password?.length >= 12, 'Supply synthetic .test credentials through the environment.');
  const admin = await LocalClient.signIn(origin, workspace, email, password);
  const clients = [admin];
  try {
    const session = await admin.call('/api/v1/session');
    assert.equal(session.user.tenantRole, 'ADMIN');
    const projects = await admin.call('/api/v1/projects');
    const project = projects.find(item => item.projectKey === projectKey) ?? await admin.call('/api/v1/projects', 'POST', {
      projectKey, name: 'Retail Lending Decisioning',
      description: 'Govern retail loan eligibility, credit-risk classification, affordability, exposure exceptions and offered rates through reviewed policy versions.',
    });
    const root = `/api/v1/projects/${project.id}`;
    const users = await admin.call('/api/v1/users');
    for (const [name, local, role] of [['Elif Author', 'elif', 'EDITOR'], ['Mert Reviewer', 'mert', 'REVIEWER'], ['Deniz Operator', 'deniz', 'VIEWER']]) {
      let user = users.find(item => item.email === `${local}@northstar.test`);
      if (!user) {
        user = await admin.call('/api/v1/users', 'POST', { email: `${local}@northstar.test`, displayName: name, tenantRole: 'MEMBER', temporaryPassword: password });
        users.push(user);
      }
      const members = await admin.call(`${root}/members`);
      if (!members.some(member => member.userId === user.id && member.role === role)) await admin.call(`${root}/members/${user.id}`, 'PUT', { role });
    }
    const author = await LocalClient.signIn(origin, workspace, 'elif@northstar.test', password);
    const reviewer = await LocalClient.signIn(origin, workspace, 'mert@northstar.test', password);
    const operator = await LocalClient.signIn(origin, workspace, 'deniz@northstar.test', password);
    clients.push(author, reviewer, operator);

    const types = await author.call(`${root}/types`);
    async function type(typeKey, name, kind, fields = [], values = []) {
      let result = types.find(item => item.typeKey === typeKey);
      if (!result) {
        result = await author.call(`${root}/types`, 'POST', { typeKey, name, kind, description: `Reusable ${name.toLowerCase()} contract for retail lending policy.`, fields, values, itemType: null });
        types.push(result);
      }
      return result;
    }
    const enumValues = values => values.map(value => ({ value, label: value.toLowerCase().replaceAll('_', ' ') }));
    const employment = await type('EMPLOYMENT_TYPE', 'Employment type', 'ENUM', [], enumValues(['SALARIED', 'SELF_EMPLOYED', 'RETIRED']));
    const residency = await type('RESIDENCY_STATUS', 'Residency status', 'ENUM', [], enumValues(['RESIDENT', 'NON_RESIDENT']));
    const purpose = await type('LOAN_PURPOSE', 'Loan purpose', 'ENUM', [], enumValues(['HOME_IMPROVEMENT', 'VEHICLE', 'DEBT_CONSOLIDATION']));
    const channel = await type('APPLICATION_CHANNEL', 'Application channel', 'ENUM', [], enumValues(['BRANCH', 'DIGITAL', 'PARTNER']));
    const applicant = await type('APPLICANT_PROFILE', 'Applicant profile', 'OBJECT', [field('age', 'Age', builtin('NUMBER')), field('residency', 'Residency', custom(residency)), field('employmentType', 'Employment', custom(employment)), field('employmentTenure', 'Employment tenure in months', builtin('NUMBER'))]);
    const credit = await type('CREDIT_PROFILE', 'Credit profile', 'OBJECT', [field('score', 'Credit score', builtin('NUMBER')), field('delinquencies', 'Recent delinquencies', builtin('NUMBER')), field('recentInquiries', 'Recent inquiries', builtin('NUMBER')), field('fraudFlag', 'Fraud-risk flag', builtin('BOOLEAN'))]);
    const finances = await type('FINANCIAL_POSITION', 'Financial position', 'OBJECT', [field('monthlyIncome', 'Monthly income', builtin('NUMBER')), field('monthlyObligations', 'Monthly obligations', builtin('NUMBER')), field('existingExposure', 'Existing exposure', builtin('NUMBER'))]);
    const loan = await type('LOAN_REQUEST', 'Loan request', 'OBJECT', [field('amount', 'Requested amount', builtin('NUMBER')), field('termMonths', 'Term in months', builtin('NUMBER')), field('purpose', 'Loan purpose', custom(purpose)), field('channel', 'Application channel', custom(channel))]);
    const application = await type('LOAN_APPLICATION', 'Loan application', 'OBJECT', [field('applicant', 'Applicant', custom(applicant)), field('credit', 'Credit', custom(credit)), field('finances', 'Finances', custom(finances)), field('request', 'Request', custom(loan))]);
    const services = await author.call(`${root}/decision-services`);
    async function service(key, name, description, outputs) {
      let result = services.find(item => item.serviceKey === key);
      if (!result) {
        result = await author.call(`${root}/decision-services`, 'POST', { serviceKey: key, name, description, inputs: [field('application', 'Loan application', custom(application))], outputs });
        services.push(result);
      }
      return result;
    }
    const main = await service(serviceKey, 'Retail loan offer', 'Assess applicant eligibility, affordability, risk and exposure before composing a policy decision and offered annual rate.', [field('decision', 'Policy decision', builtin('TEXT')), field('annualRate', 'Offered annual rate', builtin('NUMBER'))]);
    const mainRoot = `${root}/decision-services/${main.id}`;
    const id = key => requestId(`${main.id}:${key}`);

    function node(key, name, inputs, output, x, y) {
      const nodeId = id(key);
      const port = (item, direction, position) => ({ id: id(`${key}:${direction}:${item.fieldKey}`), nodeId, direction, portKey: item.fieldKey, label: item.label, type: item.type, required: true, position });
      return { id: nodeId, nodeKey: key, name, kind: 'DECISION', serviceFieldKey: null, positionX: x, positionY: y, ports: [...inputs.map((item, index) => port(item, 'INPUT', index)), port(output, 'OUTPUT', 0)] };
    }
    async function graphFor(serviceValue, definitions, connections) {
      const path = `${root}/decision-services/${serviceValue.id}/graph`;
      let graph = await author.call(path);
      if (!graph) graph = await author.call(path, 'POST');
      if (graph.nodes.some(item => item.kind === 'DECISION')) {
        const arranged = graph.nodes.map(item => {
          const position = definitions.find(candidate => candidate.nodeKey === item.nodeKey);
          return { ...item, positionX: position?.positionX ?? (item.kind === 'INPUT' ? 20 : 1070), positionY: position?.positionY ?? (item.kind === 'INPUT' ? 250 : item.serviceFieldKey === 'decision' ? 180 : 350) };
        });
        if (arranged.some((item, index) => item.positionX !== graph.nodes[index].positionX || item.positionY !== graph.nodes[index].positionY)) graph = await author.call(path, 'PUT', { version: graph.version, nodes: arranged, edges: graph.edges });
        return graph;
      }
      const contract = graph.nodes.map(item => ({ ...item, positionX: item.kind === 'INPUT' ? 20 : 1070, positionY: item.kind === 'INPUT' ? 250 : item.serviceFieldKey === 'decision' ? 180 : 350 }));
      const nodes = [...contract, ...definitions];
      const findPort = (key, direction, portKey) => {
        const owner = nodes.find(item => item.nodeKey === key);
        assert(owner, `Unknown node ${key}`);
        const port = owner.ports.find(item => item.direction === direction && (!portKey || item.portKey === portKey));
        assert(port, `Unknown port ${key}.${portKey}`);
        return port.id;
      };
      const edges = connections.map(([from, to, input, output], index) => ({ id: requestId(`${serviceValue.id}:edge:${index}`), sourcePortId: findPort(from, 'OUTPUT', output), targetPortId: findPort(to, 'INPUT', input) }));
      graph = await author.call(path, 'PUT', { version: graph.version, nodes, edges });
      assert.equal(graph.validationStatus, 'VALID', JSON.stringify(graph.issues));
      return graph;
    }
    const applicationInput = [field('application', 'Application', custom(application))];
    const result = (key, label, kind) => field(key, label, builtin(kind));
    const definitions = [
      node('eligibility', 'Applicant eligibility', applicationInput, result('eligible', 'Eligible', 'BOOLEAN'), 260, 20),
      node('affordability', 'Affordability assessment', applicationInput, result('affordable', 'Affordable', 'BOOLEAN'), 260, 170),
      node('riskBand', 'Credit-risk classification', applicationInput, result('riskBand', 'Risk band', 'TEXT'), 260, 320),
      node('exposure', 'Delegated exposure', applicationInput, result('withinExposure', 'Within limit', 'BOOLEAN'), 260, 470),
      node('policy', 'Lending policy', [result('eligible', 'Eligible', 'BOOLEAN'), result('affordable', 'Affordable', 'BOOLEAN'), result('riskBand', 'Risk band', 'TEXT'), result('withinExposure', 'Within limit', 'BOOLEAN')], result('decision', 'Policy decision', 'TEXT'), 530, 180),
      node('offerRate', 'Offer composition', [result('decision', 'Policy decision', 'TEXT'), result('riskBand', 'Risk band', 'TEXT')], result('annualRate', 'Annual rate', 'NUMBER'), 800, 350),
    ];
    const graph = await graphFor(main, definitions, [
      ...['eligibility', 'affordability', 'riskBand', 'exposure'].map(key => ['input_application', key, 'application']),
      ['eligibility', 'policy', 'eligible'], ['affordability', 'policy', 'affordable'], ['riskBand', 'policy', 'riskBand'], ['exposure', 'policy', 'withinExposure'],
      ['policy', 'offerRate', 'decision'], ['riskBand', 'offerRate', 'riskBand'], ['policy', 'output_decision', 'decision'], ['offerRate', 'output_annualRate', 'annualRate'],
    ]);
    async function expression(serviceValue, key, source) {
      const graphPath = `${root}/decision-services/${serviceValue.id}/graph`;
      const currentGraph = serviceValue.id === main.id ? graph : await author.call(graphPath);
      const target = currentGraph.nodes.find(item => item.nodeKey === key);
      const path = `${graphPath}/nodes/${target.id}/decision-expression`;
      let value = await author.call(path);
      if (!value) value = await author.call(path, 'POST');
      if (value.source !== source) value = await author.call(path, 'PUT', { version: value.version, source });
      assert.equal(value.validationStatus, 'VALID', `${key}: ${JSON.stringify(value.issues)}`);
      return value;
    }
    const policy = graph.nodes.find(item => item.nodeKey === 'policy');
    const tablePath = `${mainRoot}/graph/nodes/${policy.id}/decision-table`;
    let table = await author.call(tablePath);
    if (!table) table = await author.call(tablePath, 'POST');
    if (!table.rules.length) {
      const rules = policyRules.map((rule, index) => ({ id: id(`rule:${index}`), enabled: true, description: rule.description,
        inputs: table.inputs.map(column => ({ id: id(`rule:${index}:${column.portKey}`), inputPortId: column.portId,
          operator: Object.hasOwn(rule.when, column.portKey) ? 'EQUALS' : 'ANY', valueJson: Object.hasOwn(rule.when, column.portKey) ? JSON.stringify(rule.when[column.portKey]) : null })), outputValueJson: JSON.stringify(rule.result) }));
      table = await author.call(tablePath, 'PUT', { version: table.version, hitPolicy: 'FIRST', rules });
    }
    assert.equal(table.validationStatus, 'VALID');

    async function govern(version) {
      let workflow = await author.call(`${mainRoot}/change-requests`);
      let change = workflow.requests.find(item => item.decisionVersionId === version.id);
      if (!change) change = await author.call(`${mainRoot}/change-requests`, 'POST', { decisionVersionId: version.id,
        title: `Approve ${version.label.toLowerCase()}`, summary: 'Typed contract and ten ordered policy rules reviewed; 18 persisted boundary scenarios pass.', clientRequestId: requestId(`${main.id}:review:${version.id}`) });
      if (change.state === 'DRAFT') change = await author.call(`${mainRoot}/change-requests/${change.id}/submit`, 'POST', { expectedVersion: change.version, note: 'Submit captured policy with completed regression evidence.', clientRequestId: requestId(`${change.id}:submit`) });
      if (change.state === 'SUBMITTED') change = await reviewer.call(`${mainRoot}/change-requests/${change.id}/approve`, 'POST', { expectedVersion: change.version, note: 'Reviewed affordability, credit boundaries, ordered fallback and regression evidence.', clientRequestId: requestId(`${change.id}:approve`) });
      if (change.state === 'APPROVED') change = await admin.call(`${mainRoot}/change-requests/${change.id}/promote`, 'POST', { expectedVersion: change.version, note: 'Promote reviewed policy as the governed retail-lending baseline.', clientRequestId: requestId(`${change.id}:promote`) });
      assert.equal(change.state, 'PROMOTED');
      workflow = await admin.call(`${mainRoot}/change-requests`);
      const runtime = await admin.call(`${mainRoot}/runtime-sandbox`);
      if (!runtime.deployments.some(item => item.decisionVersionId === version.id && ['ACTIVE', 'RETIRED', 'ROLLED_BACK'].includes(item.state))) {
        const deployment = await admin.call(`${mainRoot}/runtime-sandbox/deployments`, 'POST', { decisionVersionId: version.id, expectedPromotionRevision: workflow.promotion.revision, clientRequestId: requestId(`${main.id}:deploy:${version.id}`) });
        assert.equal(deployment.state, 'ACTIVE');
      }
    }
    for (const [index, version] of policyVersions.entries()) {
      const versions = await author.call(`${mainRoot}/versions`);
      const existing = versions.find(item => item.label === version.label);
      if (existing) { await govern(existing); continue; }
      for (const [key, source] of Object.entries(expressions(version))) await expression(main, key, source);
      const savedCases = await author.call(`${mainRoot}/tests`);
      const testIds = [];
      for (const scenario of scenarios) {
        let expectedOutputs = scenario.expectedOutputs;
        if (scenario.testKey === 'thinCreditFile' && index < 2) expectedOutputs = { decision: 'REFER', annualRate: 0 };
        if (scenario.testKey === 'commitmentRefinement' && index === 0) expectedOutputs = { decision: 'APPROVE', annualRate: 6.9 };
        const body = { ...scenario, description: `Synthetic retail-lending boundary: ${scenario.name.toLowerCase()}.`, expectedOutputs };
        let saved = savedCases.find(item => item.testKey === scenario.testKey);
        if (!saved) saved = await author.call(`${mainRoot}/tests`, 'POST', body);
        else if (JSON.stringify(saved.expectedOutputs) !== JSON.stringify(expectedOutputs)) saved = await author.call(`${mainRoot}/tests/${saved.id}`, 'PUT', { ...body, version: saved.version });
        assert.equal(saved.validationStatus, 'VALID');
        testIds.push(saved.id);
      }
      const matrix = await author.call(`${mainRoot}/regression-matrices`, 'POST', { clientRequestId: requestId(`${main.id}:matrix:${index}`), testCaseIds: testIds });
      assert.equal(matrix.run.status, 'COMPLETED');
      assert.equal(matrix.run.passedCount, scenarios.length);
      assert.equal(matrix.run.failedCount + matrix.run.errorCount, 0);
      const working = await author.call(`${mainRoot}/versions/working`);
      assert.equal(working.validationStatus, 'VALID');
      const captured = await author.call(`${mainRoot}/versions`, 'POST', { label: version.label, note: version.note,
        expectedServiceVersion: working.serviceVersion, expectedGraphVersion: working.graphVersion,
        expectedFingerprint: working.fingerprint, clientRequestId: requestId(`${main.id}:capture:${index}`) });
      await govern(captured);
      console.log(`Captured V${captured.versionNumber}; ${matrix.run.passedCount} real regression cases passed.`);
    }
    const versions = await author.call(`${mainRoot}/versions`);
    await govern(versions[0]);
    let runtime = await admin.call(`${mainRoot}/runtime-sandbox`);
    if (runtime.deployments.length === 3 && !runtime.deployments.some(item => item.state === 'ROLLED_BACK')) {
      const previous = runtime.deployments.find(item => item.decisionVersionNumber === 2);
      await admin.call(`${mainRoot}/runtime-sandbox/deployments/${previous.id}/rollback`, 'POST', { expectedEnvironmentRevision: runtime.environment.revision, note: 'Verify restoration of the prior known-good affordability policy.', clientRequestId: requestId(`${main.id}:rollback-rehearsal`) });
      const restored = await operator.call(`${mainRoot}/runtime-sandbox/evaluations`, 'POST', { inputs: scenarios[0].inputs });
      assert.equal(restored.decisionVersionId, previous.decisionVersionId);
      runtime = await admin.call(`${mainRoot}/runtime-sandbox`);
      await admin.call(`${mainRoot}/runtime-sandbox/deployments`, 'POST', { decisionVersionId: versions[0].id, expectedPromotionRevision: runtime.promotionRevision, clientRequestId: requestId(`${main.id}:restore-reviewed-policy`) });
    }

    // Smaller independently executable services share the same reusable input contract.
    for (const [key, name, nodeKey, outputKey, kind] of [
      ['APPLICATION_ELIGIBILITY', 'Application eligibility', 'eligibility', 'eligible', 'BOOLEAN'],
      ['CREDIT_RISK_CLASSIFICATION', 'Credit-risk classification', 'riskBand', 'riskBand', 'TEXT'],
      ['AFFORDABILITY_ASSESSMENT', 'Affordability assessment', 'affordability', 'affordable', 'BOOLEAN'],
    ]) {
      const independent = await service(key, name, `${name} as a separately executable decision using the shared loan application contract.`, [result(outputKey, name, kind)]);
      const ownNode = node(nodeKey, name, applicationInput, result(outputKey, name, kind), 350, 200);
      // Node/port IDs are scoped independently so two service graphs never share identity.
      ownNode.id = requestId(`${independent.id}:node`);
      ownNode.ports = ownNode.ports.map(port => ({ ...port, nodeId: ownNode.id, id: requestId(`${independent.id}:${port.direction}:${port.portKey}`) }));
      await graphFor(independent, [ownNode], [['input_application', nodeKey, 'application'], [nodeKey, `output_${outputKey}`, outputKey]]);
      await expression(independent, nodeKey, expressions(policyVersions.at(-1))[nodeKey]);
    }

    // Two smaller, related portfolios make project scope meaningful, not just additional empty cards.
    const supportingProjects = [];
    for (const fixture of [
      { key: 'SME-LENDING', name: 'SME Lending Policy', description: 'Control working-capital eligibility and delegated exposure for established small businesses.',
        serviceKey: 'WORKING_CAPITAL_LIMIT', serviceName: 'Working-capital facility limit',
        inputs: [field('annualRevenue', 'Annual revenue', builtin('NUMBER')), field('tradingYears', 'Years trading', builtin('NUMBER')), field('existingExposure', 'Existing exposure', builtin('NUMBER'))],
        output: field('facilityLimit', 'Available facility limit', builtin('NUMBER')),
        source: 'if tradingYears >= 2 and annualRevenue >= 250000 then (if existingExposure < annualRevenue * 0.15 then annualRevenue * 0.15 - existingExposure else 0) else 0',
        probe: { annualRevenue: 600000, tradingYears: 5, existingExposure: 30000 }, expected: { facilityLimit: 60000 } },
      { key: 'CARD-LIMITS', name: 'Card Limit Management', description: 'Review revolving-credit limit requests against income multiples and delegated policy limits.',
        serviceKey: 'CARD_LIMIT_REVIEW', serviceName: 'Revolving-credit limit review',
        inputs: [field('monthlyIncome', 'Monthly income', builtin('NUMBER')), field('currentLimit', 'Current limit', builtin('NUMBER')), field('requestedLimit', 'Requested limit', builtin('NUMBER'))],
        output: field('decision', 'Limit decision', builtin('TEXT')),
        source: 'if requestedLimit <= currentLimit then "KEEP" else if requestedLimit <= monthlyIncome * 3 and requestedLimit <= 25000 then "APPROVE" else "REFER"',
        probe: { monthlyIncome: 5000, currentLimit: 8000, requestedLimit: 12000 }, expected: { decision: 'APPROVE' } },
    ]) {
      const allProjects = await admin.call('/api/v1/projects');
      const supporting = allProjects.find(item => item.projectKey === fixture.key) ?? await admin.call('/api/v1/projects', 'POST', { projectKey: fixture.key, name: fixture.name, description: fixture.description });
      const supportingRoot = `/api/v1/projects/${supporting.id}`;
      const members = await admin.call(`${supportingRoot}/members`);
      for (const [local, role] of [['elif', 'EDITOR'], ['mert', 'REVIEWER'], ['deniz', 'VIEWER']]) {
        const user = users.find(item => item.email === `${local}@northstar.test`);
        if (!members.some(item => item.userId === user.id && item.role === role)) await admin.call(`${supportingRoot}/members/${user.id}`, 'PUT', { role });
      }
      const catalog = await admin.call(`${supportingRoot}/decision-services`);
      let supportingChanged = !catalog.some(item => item.serviceKey === fixture.serviceKey);
      const decisionService = catalog.find(item => item.serviceKey === fixture.serviceKey) ?? await admin.call(`${supportingRoot}/decision-services`, 'POST', { serviceKey: fixture.serviceKey, name: fixture.serviceName, description: fixture.description, inputs: fixture.inputs, outputs: [fixture.output] });
      const serviceRoot = `${supportingRoot}/decision-services/${decisionService.id}`;
      let model = await admin.call(`${serviceRoot}/graph`);
      if (!model) model = await admin.call(`${serviceRoot}/graph`, 'POST');
      if (!model.nodes.some(item => item.kind === 'DECISION')) {
        supportingChanged = true;
        const nodeId = requestId(`${decisionService.id}:policy`);
        const ports = [...fixture.inputs.map((item, position) => ({ id: requestId(`${nodeId}:${item.fieldKey}`), nodeId, portKey: item.fieldKey, label: item.label, type: item.type, required: true, direction: 'INPUT', position })),
          { id: requestId(`${nodeId}:output`), nodeId, portKey: fixture.output.fieldKey, label: fixture.output.label, type: fixture.output.type, required: true, direction: 'OUTPUT', position: 0 }];
        const nodes = [...model.nodes.map(item => ({ ...item, positionX: item.kind === 'INPUT' ? 24 : 650, positionY: item.kind === 'INPUT' ? 30 + fixture.inputs.findIndex(input => input.fieldKey === item.serviceFieldKey) * 140 : 180 })),
          { id: nodeId, nodeKey: 'policy', name: fixture.serviceName, kind: 'DECISION', serviceFieldKey: null, positionX: 330, positionY: 150, ports }];
        const edges = model.nodes.map(item => ({ id: requestId(`${nodeId}:edge:${item.id}`), sourcePortId: item.kind === 'INPUT' ? item.ports[0].id : ports.at(-1).id,
          targetPortId: item.kind === 'INPUT' ? ports.find(port => port.direction === 'INPUT' && port.portKey === item.serviceFieldKey).id : item.ports[0].id }));
        model = await admin.call(`${serviceRoot}/graph`, 'PUT', { version: model.version, nodes, edges });
      }
      assert.equal(model.validationStatus, 'VALID');
      const policyNode = model.nodes.find(item => item.kind === 'DECISION');
      const path = `${serviceRoot}/graph/nodes/${policyNode.id}/decision-expression`;
      let implementation = await admin.call(path);
      if (!implementation) implementation = await admin.call(path, 'POST');
      if (implementation.source !== fixture.source) { implementation = await admin.call(path, 'PUT', { version: implementation.version, source: fixture.source }); supportingChanged = true; }
      assert.equal(implementation.validationStatus, 'VALID');
      if (supportingChanged) {
        const evaluated = await admin.call(`${serviceRoot}/sandbox/evaluations`, 'POST', { inputs: fixture.probe });
        assert.deepEqual(evaluated.outputs, fixture.expected);
      }
      supportingProjects.push({ project: supporting, service: decisionService, graphNodes: model.nodes.length });
    }

    const trafficKey = requestId(`${main.id}:traffic`);
    const existingRequests = await admin.call(`${mainRoot}/decision-api/requests`);
    if (!existingRequests.some(item => item.requestId === trafficKey)) {
      // Evaluate the real immutable runtime with a viewer, then exercise scoped machine credentials.
      for (const scenario of scenarios) {
        const evaluation = await operator.call(`${mainRoot}/runtime-sandbox/evaluations`, 'POST', { inputs: scenario.inputs });
        assert.deepEqual(evaluation.outputs, scenario.expectedOutputs);
      }
      const created = await admin.call(`${mainRoot}/api-credentials`, 'POST', { label: 'Local lending integration verification' });
      try {
        for (const scenario of scenarios) {
          const evaluation = await admin.call(`/api/runtime/v1/decisions/${serviceKey}/evaluate`, 'POST', { requestId: requestId(`${main.id}:single:${scenario.testKey}`), inputs: scenario.inputs }, { Authorization: `Bearer ${created.secret}` });
          assert.deepEqual(evaluation.outputs, scenario.expectedOutputs);
        }
        const batch = await admin.call(`/api/runtime/v1/decisions/${serviceKey}/batch`, 'POST', { requestId: trafficKey, items: scenarios.map(item => ({ correlationId: item.testKey, inputs: item.inputs })) }, { Authorization: `Bearer ${created.secret}` });
        assert.equal(batch.succeededCount, scenarios.length);
        batch.items.forEach((item, index) => { assert.equal(item.correlationId, scenarios[index].testKey); assert.deepEqual(item.outputs, scenarios[index].expectedOutputs); });
      } finally {
        await admin.call(`${mainRoot}/api-credentials/${created.credential.id}/revoke`, 'POST', { expectedVersion: created.credential.version });
      }
      const batch = await operator.call(`${mainRoot}/decision-api/batches`, 'POST', { requestId: requestId(`${main.id}:workbench-batch`), items: scenarios.slice(0, 6).map(item => ({ correlationId: item.testKey, inputs: item.inputs })) });
      assert.equal(batch.succeededCount, 6);
    }
    const impact = await admin.call(`${mainRoot}/dependency-impact`);
    const assessments = await admin.call(`${mainRoot}/dependency-impact/assessments`);
    if (!assessments.some(item => item.sourceFingerprint === impact.impact.sourceFingerprint)) await admin.call(`${mainRoot}/dependency-impact/assessments`, 'POST', { clientRequestId: requestId(`${main.id}:impact`), expectedFingerprint: impact.impact.sourceFingerprint });
    const operations = await admin.call('/api/v1/operations');
    assert.equal(operations.posture.runningMatrices, 0, 'Do not interrupt active work to populate recovery history.');
    assert.equal(operations.posture.inconsistentRuntimePointers, 0);
    if (!operations.recoveries.length) await admin.call('/api/v1/operations/recoveries', 'POST', { clientRequestId: requestId(`${project.id}:recovery`), expectedFingerprint: operations.posture.fingerprint });
    const traces = await admin.call(`${mainRoot}/decision-traces`);
    for (const trace of traces) {
      const detail = await admin.call(`${mainRoot}/decision-traces/${trace.evaluationId}`);
      assert.equal(detail.steps.length, 6);
      assert(detail.inputFingerprint && detail.outputFingerprint);
      for (const step of detail.steps) if (typeof step.result !== 'boolean') {
        assert(step.result && typeof step.result === 'object' && step.result.redacted === true, 'Non-boolean trace values must remain redacted.');
        assert.match(step.result.fingerprint, /^[a-f0-9]{64}$/);
      }
    }
    const analytics = await admin.call(`${mainRoot}/runtime-analytics?window=HOURS_24&channel=ALL`);
    const working = await admin.call(`${mainRoot}/versions/working`);
    assert.equal(working.fingerprint, versions[0].fingerprint, 'The displayed model must match its governed semantic identity.');
    const report = { synthetic: true, project, supportingProjects, services: await admin.call(`${root}/decision-services`), types: types.map(item => ({ typeKey: item.typeKey, name: item.name, kind: item.kind })),
      serviceId: main.id, graphNodes: graph.nodes.length, graphEdges: graph.edges.length, tableRules: table.rules.length, scenarios: scenarios.length,
      matrices: await admin.call(`${mainRoot}/regression-matrices`), versions: await admin.call(`${mainRoot}/versions`), workflow: await admin.call(`${mainRoot}/change-requests`), runtime: await admin.call(`${mainRoot}/runtime-sandbox`),
      runtimeTraces: traces.length, analytics: analytics.analytics, operations: await admin.call('/api/v1/operations'), graphPath: `/projects/${project.id}/services/${main.id}/graph`, policyNodeId: policy.id };
    assert.equal(report.runtime.environment.activeDeploymentId !== null, true);
    assert.equal(report.analytics.analyzedCount, traces.length);
    assert.equal(report.operations.posture.status, 'READY');
    return report;
  } finally { for (const client of clients) await client.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  assert(process.argv.includes('--allow-local-writes'), 'Explicitly pass --allow-local-writes for the disposable local workspace.');
  const report = await populate({ origin: process.env.RULECRAFT_SHOWCASE_ORIGIN ?? 'http://127.0.0.1:50111', workspace: process.env.RULECRAFT_SHOWCASE_WORKSPACE ?? 'northstar-rules', email: process.env.RULECRAFT_SHOWCASE_EMAIL, password: process.env.RULECRAFT_SHOWCASE_PASSWORD });
  if (process.env.RULECRAFT_SHOWCASE_REPORT) await writeFile(process.env.RULECRAFT_SHOWCASE_REPORT, JSON.stringify(report, null, 2));
  console.log(`Verified synthetic lending workspace: ${report.graphNodes} nodes, ${report.tableRules} rules, ${report.scenarios} scenarios, ${report.runtimeTraces} real runtime traces.`);
}

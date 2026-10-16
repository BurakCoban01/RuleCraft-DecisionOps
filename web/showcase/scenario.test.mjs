import assert from 'node:assert/strict';
import test from 'node:test';
import { localOrigin, requestId } from './client.mjs';
import { policyRules, policyVersions, scenarios } from './scenario.mjs';

test('local tooling rejects non-loopback and credential-bearing targets', () => {
  for (const value of ['https://example.test', 'http://127.0.0.1.example.test', 'http://user:secret@localhost', 'file:///tmp', 'http://localhost/api']) assert.throws(() => localOrigin(value));
  assert.equal(localOrigin('http://127.0.0.1:50111'), 'http://127.0.0.1:50111');
  assert.equal(localOrigin('http://[::1]:50111'), 'http://[::1]:50111');
});
test('retry identities are stable, distinct UUIDs', () => {
  assert.equal(requestId('policy'), requestId('policy'));
  assert.notEqual(requestId('policy'), requestId('review'));
  assert.match(requestId('policy'), /^[0-9a-f]{8}-[0-9a-f]{4}-5[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
test('the fictional policy fixture has meaningful boundaries and ordered outcomes', () => {
  assert.equal(scenarios.length, 18);
  assert.equal(new Set(scenarios.map(item => item.testKey)).size, 18);
  assert.equal(policyRules.length, 10);
  assert.equal(new Set(policyRules.map(item => item.description)).size, 10);
  assert.deepEqual(policyRules.at(-1).when, {});
  assert.deepEqual(new Set(scenarios.map(item => item.expectedOutputs.decision)), new Set(['APPROVE', 'DECLINE', 'REFER']));
  for (const scenario of scenarios) {
    const a = scenario.inputs.application;
    assert(a.request.termMonths > 0);
    const eligible = a.applicant.age >= 21 && a.applicant.residency === 'RESIDENT' && a.applicant.employmentTenure >= 6;
    const affordable = a.finances.monthlyIncome > 0 && (a.finances.monthlyObligations + a.request.amount / a.request.termMonths) / a.finances.monthlyIncome <= 0.4;
    const riskBand = a.credit.fraudFlag ? 'FRAUD' : a.credit.delinquencies >= 2 ? 'ADVERSE' : a.credit.score < 620 ? 'WEAK' : a.credit.score < 680 || a.credit.recentInquiries > 5 ? 'BORDERLINE' : a.credit.score >= 760 ? 'PRIME' : 'STANDARD';
    const values = { eligible, affordable, riskBand, withinExposure: a.request.amount + a.finances.existingExposure <= 60000 };
    const decision = policyRules.find(rule => Object.entries(rule.when).every(([key, value]) => values[key] === value)).result;
    assert.deepEqual(scenario.expectedOutputs, { decision, annualRate: decision === 'APPROVE' ? riskBand === 'PRIME' ? 6.9 : 9.9 : 0 }, scenario.testKey);
  }
  assert.equal(new Set(policyVersions.map(item => `${item.affordability}:${item.scoreFloor}`)).size, 3);
});

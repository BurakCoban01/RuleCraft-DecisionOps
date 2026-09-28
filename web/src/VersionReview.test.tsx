import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { VersionReview } from './VersionReview';
import type { DecisionGraph, DecisionService, DecisionVersionDiff, DecisionVersionSummary } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility', description: null, status: 'ACTIVE', inputs: [], outputs: [], updatedAt: '2026-08-29T00:00:00Z', version: 4 };
const graph: DecisionGraph = { id: 'g1', decisionServiceId: 's1', serviceKey: 'ELIGIBILITY', serviceName: 'Eligibility', validationStatus: 'VALID', issues: [], nodes: [], edges: [], updatedAt: '2026-08-29T00:00:00Z', version: 7 };
const working = { serviceVersion: 4, graphVersion: 7, validationStatus: 'VALID' as const, fingerprint: 'c'.repeat(64) };
const baseline: DecisionVersionSummary = { id: 'v1', versionNumber: 1, label: 'Baseline', note: 'Initial review point', validationStatus: 'VALID', fingerprint: 'a'.repeat(64), sourceServiceVersion: 3, sourceGraphVersion: 6, capturedAt: '2026-08-29T00:00:00Z' };
const candidate: DecisionVersionSummary = { id: 'v2', versionNumber: 2, label: 'Threshold update', note: 'Lower approval threshold', validationStatus: 'VALID', fingerprint: 'b'.repeat(64), sourceServiceVersion: 4, sourceGraphVersion: 7, capturedAt: '2026-08-29T01:00:00Z' };
const diff: DecisionVersionDiff = {
  base: baseline, target: candidate, breakingCount: 0, behavioralCount: 1, nonBreakingCount: 1,
  changes: [
    { category: 'IMPLEMENTATION', kind: 'CHANGED', impact: 'BEHAVIORAL', path: 'implementations.threshold', label: 'threshold', before: { source: 'score >= 700' }, after: { source: 'score >= 650' } },
    { category: 'TEST', kind: 'CHANGED', impact: 'NON_BREAKING', path: 'tests.highScore', label: 'highScore', before: { approved: true }, after: { approved: false } },
  ],
};
function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('VersionReview', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it('captures an immutable baseline with current aggregate guards', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => init?.method === 'POST' ? response(baseline, 201) : String(input).endsWith('/working') ? response(working) : response([]));
    render(<VersionReview projectId="p1" service={service} graph={graph} canEdit onBack={() => undefined} />);
    await userEvent.click(await screen.findByRole('button', { name: 'Capture baseline' }));
    await userEvent.type(screen.getByLabelText('Version label'), 'Baseline');
    await userEvent.type(screen.getByLabelText('Review note'), 'Initial review point');
    await userEvent.click(screen.getByRole('button', { name: 'Capture working decision' }));
    expect(await screen.findByText('Version 1 captured as valid.')).toBeInTheDocument();
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    const body = JSON.parse(String(post?.[1]?.body));
    expect(body).toMatchObject({ label: 'Baseline', expectedServiceVersion: 4, expectedGraphVersion: 7, expectedFingerprint: working.fingerprint });
    expect(body.clientRequestId).toBeTruthy();
  });

  it('compares versions and exposes categorized before/after evidence to viewers', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(input => String(input).includes('/diff?') ? response(diff) : String(input).endsWith('/working') ? response(working) : response([candidate, baseline]));
    render(<VersionReview projectId="p1" service={service} graph={graph} canEdit={false} onBack={() => undefined} />);
    expect(await screen.findByText('Read-only review access. Owners and editors can capture the working decision.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Capture version' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Compare versions' }));
    expect(await screen.findByRole('heading', { name: 'Change review' })).toBeInTheDocument();
    expect(screen.getByText('threshold')).toBeInTheDocument();
    await userEvent.click(screen.getAllByRole('button', { name: /Review values/ })[0]);
    expect(screen.getByText(/score >= 650/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'TEST' }));
    expect(screen.getByText('highScore')).toBeInTheDocument();
  });
});

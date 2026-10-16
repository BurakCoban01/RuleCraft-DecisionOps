import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { RuntimeAnalytics } from './RuntimeAnalytics';
import type { AnalyticsTimeBucket, DecisionService, RuntimeAnalyticsWorkspace, RuntimeBenchmark } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility', description: null, status: 'ACTIVE', inputs: [{ fieldKey: 'score', label: 'Credit score', type: { builtinType: 'NUMBER', customTypeId: null }, required: true }], outputs: [{ fieldKey: 'approved', label: 'Approved', type: { builtinType: 'BOOLEAN', customTypeId: null }, required: true }], updatedAt: '2026-08-29T08:00:00Z', version: 2 };
const buckets: AnalyticsTimeBucket[] = Array.from({ length: 24 }, (_, index) => ({ start: new Date(Date.UTC(2026, 7, 28, index)).toISOString(), end: new Date(Date.UTC(2026, 7, 28, index + 1)).toISOString(), count: index === 20 ? 2 : 0, failedCount: index === 20 ? 1 : 0, p95Micros: index === 20 ? 1_400 : 0 }));
const benchmark: RuntimeBenchmark = { id: 'b1', deploymentId: 'd1', decisionVersionId: 'v1', artifactFingerprint: 'a'.repeat(64), inputFingerprint: 'b'.repeat(64), outputFingerprint: 'c'.repeat(64), iterations: 100, warmupIterations: 10, totalDurationMicros: 40_000, minMicros: 200, meanMicros: 400, p50Micros: 350, p95Micros: 700, p99Micros: 900, maxMicros: 1_100, throughputPerSecond: 2_500, capturedByName: 'Ada Admin', capturedAt: '2026-08-29T09:00:00Z' };
const workspace: RuntimeAnalyticsWorkspace = { serviceKey: service.serviceKey, serviceName: service.name, activeArtifact: { deploymentId: 'd1', sequenceNumber: 1, decisionVersionId: 'v1', artifactFingerprint: 'a'.repeat(64) }, benchmarks: [], canBenchmark: true, accessRole: 'ADMIN', analytics: { window: 'HOURS_24', channel: 'ALL', from: '2026-08-28T10:00:00Z', to: '2026-08-29T10:00:00Z', latestEventAt: '2026-08-29T08:00:00Z', analyzedCount: 2, totalAvailable: 2, truncated: false, succeededCount: 1, failedCount: 1, successRate: 50, meanMicros: 900, p50Micros: 400, p95Micros: 1_400, p99Micros: 1_400, maxMicros: 1_400, bucketGrain: 'HOURLY', buckets, channels: [{ channel: 'API_SINGLE', count: 2, succeededCount: 1, failedCount: 1, p95Micros: 1_400 }], errors: [{ code: 'RC-DEPLOY-INPUT-TYPE', count: 1 }] } };
function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('RuntimeAnalytics', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it('renders source-backed KPIs, volume, channel, and error diagnostics', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(workspace));
    render(<RuntimeAnalytics projectId="p1" service={service} types={[]} onBack={() => undefined}/>);
    expect(await screen.findByRole('heading', { name: 'Runtime analytics' })).toBeInTheDocument();
    expect(screen.getByText('50.0%')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: /Evaluation volume over time/ })).toBeInTheDocument();
    expect(screen.getByText('API SINGLE', { selector: '.channel-bars strong' })).toBeInTheDocument();
    expect(screen.getByText('RC-DEPLOY-INPUT-TYPE')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Evidence window'), 'DAYS_7');
    expect(await screen.findByText('Persisted decision traces')).toBeInTheDocument();
  });

  it('runs a bounded typed benchmark and exposes immutable provenance', async () => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => init?.method === 'POST' ? response(benchmark, 201) : response(workspace));
    render(<RuntimeAnalytics projectId="p1" service={service} types={[]} onBack={() => undefined}/>);
    await userEvent.type(await screen.findByLabelText(/Credit score/), '710');
    await userEvent.click(screen.getByRole('button', { name: 'Run benchmark' }));
    expect(await screen.findByText('Benchmark completed across 100 measured iterations.')).toBeInTheDocument();
    expect(screen.getByText('2500.0/s')).toBeInTheDocument();
    expect(screen.getByText(/input bbbbbbbbbbbb/)).toBeInTheDocument();
    const body = JSON.parse(String(fetch.mock.calls.find(call => call[1]?.method === 'POST')?.[1]?.body));
    expect(body).toMatchObject({ expectedDeploymentId: 'd1', iterations: 100, warmupIterations: 10, inputs: { score: 710 } });
  });

  it('shows truthful empty, permission, and inactive-runtime states', async () => {
    const empty: RuntimeAnalyticsWorkspace = { ...workspace, activeArtifact: null, canBenchmark: false, accessRole: 'VIEWER', analytics: { ...workspace.analytics, analyzedCount: 0, totalAvailable: 0, succeededCount: 0, failedCount: 0, successRate: 0, latestEventAt: null, channels: [], errors: [], buckets: buckets.map(item => ({ ...item, count: 0, failedCount: 0, p95Micros: 0 })) } };
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(empty));
    render(<RuntimeAnalytics projectId="p1" service={service} types={[]} onBack={() => undefined}/>);
    expect(await screen.findByRole('heading', { name: 'No runtime evidence in this window' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Benchmark unavailable' })).toBeInTheDocument();
    expect(screen.getByText('Read-only benchmark access. Authors and reviewers can run bounded diagnostics.')).toBeInTheDocument();
  });
});

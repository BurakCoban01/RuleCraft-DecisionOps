import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ImpactGovernance } from './ImpactGovernance';
import type { ImpactSnapshot, ImpactWorkspace } from './api';

const impact: ImpactSnapshot = { serviceId: 's1', serviceKey: 'ELIGIBILITY', serviceName: 'Eligibility', generatedAt: '2026-08-29T00:00:00Z', sourceFingerprint: 'a'.repeat(64), workingValidationStatus: 'VALID', riskLevel: 'HIGH', readyForReview: false, summary: { upstreamTypes: 1, graphNodes: 3, testCases: 1, versions: 1, openRequests: 1, activeDeployments: 1, regressionRuns: 1, totalDependencies: 9, blockers: 1, warnings: 2 }, findings: [{ code: 'RC-IMPACT-REGRESSION-RISK', severity: 'HIGH', title: 'Latest regression is not clean', detail: 'One scenario failed.', action: 'Complete a clean regression matrix before promotion.', referenceId: 'r1' }], items: [
  { category: 'UPSTREAM', kind: 'TYPE', referenceId: 't1', label: 'Applicant profile', detail: 'APPLICANT · OBJECT', status: 'REFERENCED', risk: 'LOW' },
  { category: 'MODEL', kind: 'GRAPH_NODE', referenceId: 'n1', label: 'Approval threshold', detail: 'threshold · 1 upstream / 1 downstream · EXPRESSION · VALID', status: 'DECISION', risk: 'LOW' },
  { category: 'ASSURANCE', kind: 'REGRESSION_RUN', referenceId: 'r1', label: 'Regression abcdef12', detail: '1 scenarios · 1 failed · 0 errors', status: 'COMPLETED', risk: 'HIGH' },
] };
const workspace: ImpactWorkspace = { impact, canAssess: true, accessRole: 'EDITOR' };
function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('ImpactGovernance', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it('shows governed findings and filters the blast radius', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(input => String(input).endsWith('/assessments') ? response([]) : response(workspace));
    render(<ImpactGovernance projectId="p1" serviceId="s1" onBack={() => undefined}/>);
    expect(await screen.findByRole('heading', { name: 'Impact analysis' })).toBeInTheDocument();
    expect(screen.getByText('Latest regression is not clean')).toBeInTheDocument();
    expect(screen.getByText('Applicant profile')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'ASSURANCE' }));
    expect(screen.queryByText('Applicant profile')).not.toBeInTheDocument();
    expect(screen.getByText('Regression abcdef12')).toBeInTheDocument();
  });

  it('captures immutable evidence and can return to the live model', async () => {
    const assessment = { id: 'i1', sourceFingerprint: impact.sourceFingerprint, snapshotFingerprint: 'b'.repeat(64), riskLevel: 'HIGH' as const, dependencyCount: 9, findingCount: 1, blockerCount: 1, capturedByName: 'Ada Admin', capturedAt: '2026-08-29T01:00:00Z' };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => init?.method === 'POST' ? response({ assessment, impact }, 201) : String(input).endsWith('/assessments') ? response([]) : response(workspace));
    render(<ImpactGovernance projectId="p1" serviceId="s1" onBack={() => undefined}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Capture assessment' }));
    expect(await screen.findByText('Immutable impact assessment captured.')).toBeInTheDocument();
    expect(screen.getByText('Immutable assessment')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Return to live model' }));
    expect(screen.queryByText('Immutable assessment')).not.toBeInTheDocument();
  });

  it('renders viewer permission and recovery states', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(input => String(input).endsWith('/assessments') ? response([]) : response({ ...workspace, canAssess: false, accessRole: 'VIEWER' }));
    render(<ImpactGovernance projectId="p1" serviceId="s1" onBack={() => undefined}/>);
    expect(await screen.findByText('Read-only governance access. Authors and reviewers can capture immutable assessments.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Capture assessment' })).not.toBeInTheDocument();
  });
});

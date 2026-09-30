import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApprovalPipeline } from './ApprovalPipeline';
import type { ApprovalWorkflow, ChangeRequestDetail, ChangeRequestView, DecisionService, DecisionVersionSummary } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility', description: null, status: 'ACTIVE', inputs: [], outputs: [], updatedAt: '2026-08-29T00:00:00Z', version: 4 };
const version: DecisionVersionSummary = { id: 'v1', versionNumber: 1, label: 'Baseline', note: null, validationStatus: 'VALID', fingerprint: 'a'.repeat(64), sourceServiceVersion: 4, sourceGraphVersion: 7, capturedAt: '2026-08-29T00:00:00Z' };
const submitted: ChangeRequestView = { id: 'cr1', requestNumber: 1, decisionVersionId: 'v1', decisionVersionNumber: 1, decisionVersionLabel: 'Baseline', validationStatus: 'VALID', title: 'Approve baseline', summary: 'Evidence ready', state: 'SUBMITTED', createdByName: 'Ada Admin', createdAt: '2026-08-29T01:00:00Z', updatedAt: '2026-08-29T01:10:00Z', version: 1, stale: false };
const detail: ChangeRequestDetail = { request: submitted, events: [{ id: 'e1', eventType: 'SUBMITTED', fromState: 'DRAFT', toState: 'SUBMITTED', note: 'Ready', actorName: 'Ada Admin', createdAt: '2026-08-29T01:10:00Z' }] };
const empty: ApprovalWorkflow = { requests: [], promotion: null, accessRole: 'OWNER' };
function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('ApprovalPipeline', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it('opens a draft only for the latest valid immutable version', async () => {
    const created = { ...submitted, state: 'DRAFT' as const, version: 0 };
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'POST' ? response(created, 201) : response(empty));
    render(<ApprovalPipeline projectId="p1" service={service} versions={[version]} canEdit/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Open change request' }));
    await userEvent.type(screen.getByLabelText('Request title'), 'Approve baseline');
    await userEvent.type(screen.getByLabelText('Change summary'), 'Evidence ready');
    await userEvent.click(screen.getByRole('button', { name: 'Create draft' }));
    expect(await screen.findByText('CR-1 opened for V1.')).toBeInTheDocument();
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({ decisionVersionId: 'v1', title: 'Approve baseline' });
  });
  it('gives reviewers explicit decisions and records a required change note', async () => {
    const workflow: ApprovalWorkflow = { requests: [submitted], promotion: null, accessRole: 'REVIEWER' };
    const changed = { ...submitted, state: 'CHANGES_REQUESTED' as const, version: 2 };
    const fetch = vi.spyOn(globalThis, 'fetch').mockImplementation((input, init) => init?.method === 'POST' ? response(changed) : String(input).endsWith('/cr1') ? response(detail) : response(workflow));
    render(<ApprovalPipeline projectId="p1" service={service} versions={[version]} canEdit={false}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Request changes' }));
    await userEvent.type(screen.getByLabelText('Decision note'), 'Add a low-score boundary scenario');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm action' }));
    expect(await screen.findByText('CR-1 moved to changes requested.')).toBeInTheDocument();
    const post = fetch.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(String(post?.[0])).toContain('/request-changes');
    expect(JSON.parse(String(post?.[1]?.body))).toMatchObject({ expectedVersion: 1, note: 'Add a low-score boundary scenario' });
  });
});

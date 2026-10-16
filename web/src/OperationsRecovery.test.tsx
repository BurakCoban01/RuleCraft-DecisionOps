import { act, cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, expect, it, vi } from 'vitest';
import { OperationsConsole } from './OperationsConsole';
import { api } from './api';

afterEach(() => { cleanup(); vi.restoreAllMocks(); });

it('announces recovery only after its persisted history has refreshed', async () => {
  const workspace: Awaited<ReturnType<typeof api.operations>> = {
    posture: { status: 'READY', fingerprint: 'a'.repeat(64), checkedAt: '2026-10-16T12:00:00Z', schemaVersion: '16', runningMatrices: 0, inconsistentRuntimePointers: 0, checks: [] },
    security: { csrfProtected: true, secureHeaders: true, apiKeysHashed: true, correlationIds: true, failedLoginLimit: 5, runtimeRequestsPerMinute: 120, loginDenials: 0, runtimeDenials: 0, trackedRateLimitKeys: 0 },
    processUptimeSeconds: 60, recoveries: [],
  };
  const run: Awaited<ReturnType<typeof api.runRecovery>> = { id: 'recovery', sourceFingerprint: 'a'.repeat(64), resultFingerprint: 'a'.repeat(64), interruptedMatrices: 0, clearedRuntimePointers: 0, status: 'READY', executedByName: 'Ada Admin', executedAt: '2026-10-16T12:01:00Z' };
  let finishRefresh!: (value: Awaited<ReturnType<typeof api.operations>>) => void;
  vi.spyOn(api, 'operations').mockResolvedValueOnce(workspace).mockImplementationOnce(() => new Promise(resolve => { finishRefresh = resolve; }));
  vi.spyOn(api, 'runRecovery').mockResolvedValue(run);
  render(<OperationsConsole/>);
  await userEvent.click(await screen.findByRole('button', { name: 'Run recovery scan' }));
  expect(screen.queryByText(/Recovery scan completed/)).not.toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Run recovery scan' })).toBeDisabled();
  await act(async () => finishRefresh({ ...workspace, recoveries: [run] }));
  expect(await screen.findByText(/Recovery scan completed/)).toBeInTheDocument();
  expect(screen.getByText('0 matrices · 0 pointers')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Run recovery scan' })).toBeEnabled();
});

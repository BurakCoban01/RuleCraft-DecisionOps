import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { TableEditor } from './TableEditor';
import type { DecisionService, DecisionTable, GraphNode } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility', description: null, status: 'ACTIVE', inputs: [], outputs: [], updatedAt: '2026-08-29T00:00:00Z', version: 0 };
const node: GraphNode = { id: 'n1', nodeKey: 'eligibilityDecision', name: 'Eligibility decision', kind: 'DECISION', serviceFieldKey: null, positionX: 200, positionY: 100, ports: [] };
const empty: DecisionTable = { id: 't1', graphId: 'g1', nodeId: 'n1', nodeKey: node.nodeKey, nodeName: node.name, hitPolicy: 'UNIQUE', validationStatus: 'INVALID', issues: [{ code: 'RC-TABLE-NO-ENABLED-RULES', message: 'Add or enable at least one rule.', ruleId: null, portId: null }], inputs: [{ portId: 'p1', portKey: 'score', label: 'Score', type: { builtinType: 'NUMBER', customTypeId: null }, required: true }], output: { portId: 'p2', portKey: 'approved', label: 'Approved', type: { builtinType: 'BOOLEAN', customTypeId: null }, required: true }, rules: [], updatedAt: '2026-08-29T00:00:00Z', version: 0 };
const valid: DecisionTable = { ...empty, validationStatus: 'VALID', issues: [], version: 1, rules: [{ id: 'r1', enabled: true, description: 'Approve high scores', inputs: [{ id: 'e1', inputPortId: 'p1', operator: 'EQUALS', valueJson: '700' }], outputValueJson: 'true' }] };
function response(value: unknown, status = 200) { return Promise.resolve(new Response(value === null ? null : JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('TableEditor', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it('initializes a missing table and exposes a typed rule grid', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'POST' ? response(empty, 201) : response(null, 204));
    render(<TableEditor projectId="p1" service={service} node={node} types={[]} canEdit onBack={() => undefined}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Initialize decision table' }));
    await userEvent.click(await screen.findByRole('button', { name: 'Add rule' }));
    expect(screen.getByRole('columnheader', { name: 'WHENScoreNUMBER' })).toBeInTheDocument();
    expect(screen.getByLabelText('Rule 1 Score operator')).toHaveValue('ANY');
    expect(screen.getByLabelText('Rule 1 Approved result')).toBeInTheDocument();
  });
  it('shows persisted rules without authoring actions to a viewer', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(valid));
    render(<TableEditor projectId="p1" service={service} node={node} types={[]} canEdit={false} onBack={() => undefined}/>);
    expect(await screen.findByText('Read-only access. Owners and editors can change this decision table.')).toBeInTheDocument();
    expect(screen.getByLabelText('Rule 1 Score value')).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Add rule' })).not.toBeInTheDocument();
  });
});

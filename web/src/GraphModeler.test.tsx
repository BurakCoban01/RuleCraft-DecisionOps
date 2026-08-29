import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GraphModeler } from './GraphModeler';
import type { DecisionGraph, DecisionService } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility decision', description: null, status: 'ACTIVE', inputs: [{ fieldKey: 'score', label: 'Score', type: { builtinType: 'NUMBER', customTypeId: null }, required: true }], outputs: [{ fieldKey: 'approved', label: 'Approved', type: { builtinType: 'BOOLEAN', customTypeId: null }, required: true }], updatedAt: '2026-08-29T00:00:00Z', version: 0 };
const graph: DecisionGraph = { id: 'g1', decisionServiceId: 's1', serviceKey: 'ELIGIBILITY', serviceName: 'Eligibility decision', validationStatus: 'INVALID', updatedAt: '2026-08-29T00:00:00Z', version: 1, edges: [], issues: [{ severity: 'ERROR', code: 'RC-GRAPH-DECISION-MISSING', message: 'Add at least one decision node.', nodeId: null, portId: null }], nodes: [
  { id: 'n1', nodeKey: 'input_score', name: 'Score', kind: 'INPUT', serviceFieldKey: 'score', positionX: 80, positionY: 80, ports: [{ id: 'p1', nodeId: 'n1', direction: 'OUTPUT', portKey: 'score', label: 'Score', type: { builtinType: 'NUMBER', customTypeId: null }, required: true, position: 0 }] },
  { id: 'n2', nodeKey: 'output_approved', name: 'Approved', kind: 'OUTPUT', serviceFieldKey: 'approved', positionX: 980, positionY: 80, ports: [{ id: 'p2', nodeId: 'n2', direction: 'INPUT', portKey: 'approved', label: 'Approved', type: { builtinType: 'BOOLEAN', customTypeId: null }, required: true, position: 0 }] },
] };

function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('GraphModeler', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });

  it('recovers an absent graph by initializing persisted contract nodes', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'POST' ? response(graph, 201) : Promise.resolve(new Response(null, { status: 204 })));
    render(<GraphModeler projectId="p1" service={service} types={[]} canEdit onBack={() => undefined}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Initialize decision graph' }));
    expect(await screen.findByText('Graph initialized from the service contract.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '1 issue to resolve' })).toBeInTheDocument();
    expect(screen.getByText('Score', { selector: '.graph-node strong' })).toBeInTheDocument();
  });

  it('renders a persisted graph read-only for viewers', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).endsWith('/decision-tables') ? response([]) : response(graph));
    render(<GraphModeler projectId="p1" service={service} types={[]} canEdit={false} onBack={() => undefined}/>);
    expect(await screen.findByText('Read-only access. Owners and editors can change this graph.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Add decision' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Save graph' })).not.toBeInTheDocument();
  });
});

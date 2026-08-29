import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ExpressionEditor } from './ExpressionEditor';
import type { DecisionExpression, DecisionService, GraphNode } from './api';

const service: DecisionService = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility', description: null, status: 'ACTIVE', inputs: [], outputs: [], updatedAt: '2026-08-29T00:00:00Z', version: 0 };
const node: GraphNode = { id: 'n1', nodeKey: 'eligibilityExpression', name: 'Eligibility expression', kind: 'DECISION', serviceFieldKey: null, positionX: 200, positionY: 100, ports: [] };
const empty: DecisionExpression = { id: 'x1', graphId: 'g1', nodeId: 'n1', nodeKey: node.nodeKey, nodeName: node.name, languageVersion: 'RCEL-1', source: '', validationStatus: 'INVALID', issues: [{ code: 'RC-EXPR-EMPTY', message: 'Enter an expression.', line: 1, column: 1 }], inferredType: null, inputs: [{ key: 'score', label: 'Score', type: { builtinType: 'NUMBER', customTypeId: null } }], output: { key: 'approved', label: 'Approved', type: { builtinType: 'BOOLEAN', customTypeId: null } }, updatedAt: '2026-08-29T00:00:00Z', version: 0 };
const valid: DecisionExpression = { ...empty, source: 'score >= 700', validationStatus: 'VALID', issues: [], inferredType: 'BOOLEAN', version: 1 };
function response(value: unknown, status = 200) { return Promise.resolve(new Response(value === null ? null : JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('ExpressionEditor', () => {
  afterEach(() => { cleanup(); vi.restoreAllMocks(); });
  it('initializes, authors, and saves a typed expression', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'POST' ? response(empty, 201) : init?.method === 'PUT' ? response(valid) : response(null, 204));
    render(<ExpressionEditor projectId="p1" service={service} node={node} types={[]} canEdit onBack={() => undefined}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Initialize expression' }));
    await userEvent.type(screen.getByLabelText('Expression source'), 'score >= 700');
    await userEvent.click(screen.getByRole('button', { name: 'Save expression' }));
    expect(await screen.findByText('Expression saved and validated.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Expression is type-safe' })).toBeInTheDocument();
  });
  it('shows a persisted expression read-only to viewers', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => response(valid));
    render(<ExpressionEditor projectId="p1" service={service} node={node} types={[]} canEdit={false} onBack={() => undefined}/>);
    expect(await screen.findByText('Read-only access. Owners and editors can change this expression.')).toBeInTheDocument();
    expect(screen.getByLabelText('Expression source')).toHaveAttribute('readonly');
    expect(screen.queryByRole('button', { name: 'Save expression' })).not.toBeInTheDocument();
  });
  it('requires confirmation before removing an expression implementation', async () => {
    const onBack = vi.fn();
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => init?.method === 'DELETE' ? response(null, 204) : response(valid));
    render(<ExpressionEditor projectId="p1" service={service} node={node} types={[]} canEdit onBack={onBack}/>);
    await userEvent.click(await screen.findByRole('button', { name: 'Remove expression' }));
    expect(screen.getByRole('button', { name: 'Confirm remove' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Confirm remove' }));
    await waitFor(() => expect(onBack).toHaveBeenCalledOnce());
  });
});

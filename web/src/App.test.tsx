import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const manifest = { product: 'RuleCraft DecisionOps', version: '0.1.0', status: 'ready', serverTime: '2026-08-29T00:00:00Z' };
const anonymous = { authenticated: false, user: null };
const authenticated = { authenticated: true, user: { id: 'u1', tenantId: 't1', workspace: 'acme-rules', displayName: 'Ada Admin', email: 'admin@example.test', tenantRole: 'ADMIN' } };

function response(value: unknown, status = 200) { return Promise.resolve(new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })); }

describe('App', () => {
  afterEach(() => { cleanup(); localStorage.clear(); vi.restoreAllMocks(); history.replaceState({}, '', '/'); });

  it('opens the private sign-in flow after confirming platform and session state', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).includes('/platform') ? response(manifest) : response(anonymous));
    render(<App />);
    expect(screen.getByText('Opening workspace')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Sign in to RuleCraft' })).toBeInTheDocument();
    expect(screen.getByLabelText('Workspace')).toBeInTheDocument();
  });

  it('signs in and loads the persisted project catalog', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/platform')) return response(manifest);
      if (url.includes('/session/login')) return response(authenticated);
      if (url.endsWith('/session')) return response(anonymous);
      if (url.endsWith('/projects')) return response([]);
      return response({});
    });
    render(<App />);
    await userEvent.type(await screen.findByLabelText('Workspace'), 'acme-rules');
    await userEvent.type(screen.getByLabelText('Email'), 'admin@example.test');
    await userEvent.type(screen.getByLabelText('Password'), 'CorrectHorseBattery12!');
    await userEvent.click(screen.getByRole('button', { name: 'Continue to workspace' }));
    expect(await screen.findByRole('heading', { name: 'Projects' })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Create the first governed project' })).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Team access/ })).toHaveLength(2);
  });

  it('shows an actionable recovery state and retries platform and session checks', async () => {
    const request = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('network'))
      .mockImplementationOnce(() => response(manifest))
      .mockImplementationOnce(() => response(anonymous));
    render(<App />);
    const retry = await screen.findByRole('button', { name: 'Try connection again' });
    await userEvent.click(retry);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(3));
    expect(await screen.findByRole('heading', { name: 'Sign in to RuleCraft' })).toBeInTheDocument();
  });

  it('opens the persisted project catalog with service and type states', async () => {
    const project = { id: 'p1', projectKey: 'PRICING', name: 'Pricing decisions', description: 'Governed pricing', accessRole: 'OWNER', createdAt: '2026-08-29T00:00:00Z', version: 0 };
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/platform')) return response(manifest);
      if (url.endsWith('/session')) return response(authenticated);
      if (url.endsWith('/decision-services')) return response([]);
      if (url.endsWith('/types')) return response([]);
      if (url.endsWith('/members')) return response([]);
      if (url.endsWith('/projects')) return response([project]);
      if (url.endsWith('/users')) return response([]);
      return response({});
    });
    render(<App />);
    await userEvent.click(await screen.findByRole('button', { name: /PRICING Pricing decisions/ }));
    expect(await screen.findByRole('heading', { name: 'No decision services yet' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Types/ }));
    expect(await screen.findByRole('heading', { name: 'No custom types yet' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New type' })).toBeInTheDocument();
  });

  it('restores graph descendants from a direct service URL', async () => {
    const project = { id: 'p1', projectKey: 'PRICING', name: 'Pricing decisions', description: null, accessRole: 'OWNER', createdAt: '2026-08-29T00:00:00Z', version: 0 };
    const service = { id: 's1', serviceKey: 'ELIGIBILITY', name: 'Eligibility graph', description: null, status: 'ACTIVE', inputs: [], outputs: [], updatedAt: '2026-08-29T00:00:00Z', version: 0 };
    history.replaceState({}, '', '/projects/p1/services/s1/graph/nodes/n1/table');
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => {
      const url = String(input);
      if (url.includes('/platform')) return response(manifest);
      if (url.endsWith('/session')) return response(authenticated);
      if (url.endsWith('/decision-services')) return response([service]);
      if (url.endsWith('/types') || url.endsWith('/members') || url.endsWith('/users')) return response([]);
      if (url.endsWith('/projects')) return response([project]);
      if (url.endsWith('/graph')) return Promise.resolve(new Response(null, { status: 204 }));
      return response([]);
    });
    render(<App />);
    expect(await screen.findByRole('heading', { name: 'No graph has been initialized' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Eligibility graph' })).toBeInTheDocument();
  });

  it('persists an explicit theme choice', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation((input) => String(input).includes('/platform') ? response(manifest) : response(anonymous));
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: 'Use dark theme' }));
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('rulecraft-theme')).toBe('dark');
  });
});

import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';

const manifest = {
  product: 'RuleCraft DecisionOps',
  version: '0.1.0',
  status: 'ready',
  serverTime: '2026-08-29T00:00:00Z',
};

describe('App', () => {
  afterEach(() => {
    cleanup();
    localStorage.clear();
    vi.restoreAllMocks();
  });

  it('moves from loading to the connected private-workspace state', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(manifest), { status: 200 }));
    render(<App />);
    expect(screen.getByText('Opening workspace')).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Decision workspace' })).toBeInTheDocument();
    expect(screen.getByText('Connected')).toBeInTheDocument();
  });

  it('shows an actionable recovery state and retries the request', async () => {
    const request = vi.spyOn(globalThis, 'fetch')
      .mockRejectedValueOnce(new TypeError('network'))
      .mockResolvedValueOnce(new Response(JSON.stringify(manifest), { status: 200 }));
    render(<App />);
    const retry = await screen.findByRole('button', { name: 'Try connection again' });
    await userEvent.click(retry);
    await waitFor(() => expect(request).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('Private workspace available')).toBeInTheDocument();
  });

  it('persists an explicit theme choice', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify(manifest), { status: 200 }));
    render(<App />);
    await userEvent.click(screen.getAllByRole('button', { name: 'Use dark theme' })[0]);
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(localStorage.getItem('rulecraft-theme')).toBe('dark');
  });
});

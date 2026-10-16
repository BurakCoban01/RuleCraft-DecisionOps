import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import { connectionPath, fitScale, GraphViewport } from './GraphViewport';
import type { DecisionGraph } from './api';

const graph: DecisionGraph = { id: 'g', decisionServiceId: 's', serviceKey: 'POLICY', serviceName: 'Policy', validationStatus: 'VALID', issues: [], nodes: [], edges: [], updatedAt: '2026-10-16T12:00:00Z', version: 1 };
describe('GraphViewport', () => {
  afterEach(cleanup);
  it('fits measured content in both dimensions without magnifying sparse models', () => {
    expect(fitScale(1000, 700, 1200, 900)).toBeCloseTo(668 / 900);
    expect(fitScale(1000, 700, 400, 300)).toBe(1);
    expect(fitScale(360, 400, 4000, 4000)).toBe(0.25);
  });
  it('routes forward dependencies below an intervening node instead of through its labels', () => {
    const path = connectionPath({ x: 208, y: 100 }, { x: 700, y: 150 }, [{ left: 300, right: 508, top: 50, bottom: 220 }]);
    expect(path).toContain('240');
    expect(path).toMatch(/^M 208 100/);
    expect(path).toMatch(/700 150$/);
    expect(connectionPath({ x: 208, y: 100 }, { x: 300, y: 100 }, [])).not.toContain(' L ');
  });
  it('provides keyboard-reachable fit, zoom and actual-size controls without mutating the model', async () => {
    render(<GraphViewport graph={graph}><p>Typed model</p></GraphViewport>);
    const user = userEvent.setup();
    expect(screen.getByRole('region', { name: 'Decision graph canvas' })).toHaveAttribute('tabindex', '0');
    await user.click(screen.getByRole('button', { name: 'Actual size' }));
    expect(screen.getByLabelText('Graph zoom')).toHaveTextContent('100%');
    await user.click(screen.getByRole('button', { name: 'Zoom out' }));
    expect(screen.getByLabelText('Graph zoom')).toHaveTextContent('90%');
    await user.click(screen.getByRole('button', { name: 'Fit to content' }));
    expect(screen.getByLabelText('Graph zoom')).toHaveTextContent('100%');
    expect(graph.version).toBe(1);
  });
});

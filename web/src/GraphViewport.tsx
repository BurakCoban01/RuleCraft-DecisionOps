import { useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { DecisionGraph } from './api';

type Point = { x: number; y: number };
type Box = { left: number; right: number; top: number; bottom: number };
type Geometry = { width: number; height: number; ports: Record<string, Point>; boxes: Box[] };

export function connectionPath(source: Point, target: Point, boxes: Box[]) {
  const obstacles = boxes.filter(box => box.left > source.x + 8 && box.right < target.x - 8 && box.top < Math.max(source.y, target.y) + 8 && box.bottom > Math.min(source.y, target.y) - 8);
  if (obstacles.length) {
    const lane = Math.max(...obstacles.map(box => box.bottom)) + 20;
    const lead = Math.min(24, (Math.min(...obstacles.map(box => box.left)) - source.x) / 2, (target.x - Math.max(...obstacles.map(box => box.right))) / 2);
    return `M ${source.x} ${source.y} C ${source.x + lead} ${source.y}, ${source.x + lead} ${lane}, ${source.x + lead * 2} ${lane} L ${target.x - lead * 2} ${lane} C ${target.x - lead} ${lane}, ${target.x - lead} ${target.y}, ${target.x} ${target.y}`;
  }
  const bend = Math.max(40, Math.abs(target.x - source.x) / 2);
  return `M ${source.x} ${source.y} C ${source.x + bend} ${source.y}, ${target.x - bend} ${target.y}, ${target.x} ${target.y}`;
}

export function fitScale(width: number, height: number, contentWidth: number, contentHeight: number) {
  return Math.max(0.25, Math.min(1, (width - 32) / contentWidth, (height - 32) / contentHeight));
}

/** The graph stays in model coordinates; zoom affects only this bounded spatial viewport. */
export function GraphViewport({ graph, children }: { graph: DecisionGraph; children: ReactNode }) {
  const shell = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLDivElement>(null);
  const [geometry, setGeometry] = useState<Geometry>({ width: 1000, height: 700, ports: {}, boxes: [] });
  const [available, setAvailable] = useState({ width: 1000, height: 700 });
  const [manualScale, setManualScale] = useState<number | null>(null);
  const [overviewRequested, setOverviewRequested] = useState(false);
  const scale = manualScale ?? (available.width < 600 && !overviewRequested ? 1 : fitScale(available.width, available.height, geometry.width, geometry.height));

  useLayoutEffect(() => {
    const measure = () => {
      if (!shell.current || !canvas.current) return;
      const next: Geometry = { width: 320, height: 240, ports: {}, boxes: [] };
      for (const node of graph.nodes) {
        const element = canvas.current.querySelector<HTMLElement>(`[data-node-id="${node.id}"]`);
        if (!element) continue;
        const width = element.offsetWidth || 208;
        next.boxes.push({ left: node.positionX, right: node.positionX + width, top: node.positionY, bottom: node.positionY + (element.offsetHeight || 180) });
        next.width = Math.max(next.width, node.positionX + width + 32);
        next.height = Math.max(next.height, node.positionY + (element.offsetHeight || 180) + 32);
        for (const port of node.ports) {
          const anchor = element.querySelector<HTMLElement>(`[data-port-id="${port.id}"]`);
          if (anchor) next.ports[port.id] = {
            x: node.positionX + (port.direction === 'OUTPUT' ? width : 0),
            y: node.positionY + anchor.offsetTop + anchor.offsetHeight / 2,
          };
        }
      }
      setGeometry(previous => JSON.stringify(previous) === JSON.stringify(next) ? previous : next);
      const size = { width: shell.current.clientWidth, height: Math.max(360, Math.min(832, window.innerHeight - shell.current.getBoundingClientRect().top - 24)) };
      if (size.width && size.height) setAvailable(previous => previous.width === size.width && previous.height === size.height ? previous : size);
    };
    measure();
    window.addEventListener('resize', measure);
    if (typeof ResizeObserver === 'undefined') return () => window.removeEventListener('resize', measure);
    const observer = new ResizeObserver(measure);
    if (shell.current) observer.observe(shell.current);
    canvas.current?.querySelectorAll('.graph-node').forEach(element => observer.observe(element));
    return () => { observer.disconnect(); window.removeEventListener('resize', measure); };
  }, [graph, children]);

  function zoom(delta: number) { setManualScale(Math.max(0.25, Math.min(1.5, Math.round((scale + delta) * 100) / 100))); }
  function fit() {
    setOverviewRequested(true);
    setManualScale(null);
    if (shell.current) { shell.current.scrollLeft = 0; shell.current.scrollTop = 0; }
  }
  return <div className="graph-viewport">
    <div className="graph-view-controls" aria-label="Graph viewport controls">
      <span>{graph.nodes.length} nodes <span aria-hidden="true">·</span> {graph.edges.length} connections</span>
      <div>
        <button className="text-button" aria-label="Zoom out" disabled={scale <= 0.25} onClick={() => zoom(-0.1)}>−</button>
        <output aria-label="Graph zoom">{Math.round(scale * 100)}%</output>
        <button className="text-button" aria-label="Zoom in" disabled={scale >= 1.5} onClick={() => zoom(0.1)}>+</button>
        <button className="text-button" onClick={fit}>Fit to content</button>
        <button className="text-button" onClick={() => setManualScale(1)}>Actual size</button>
      </div>
    </div>
    <div ref={shell} className="graph-canvas-shell" style={{ blockSize: available.height }} role="region" aria-label="Decision graph canvas" tabIndex={0} onPointerDownCapture={event => { if ((event.target as HTMLElement).closest('.graph-node > header')) setManualScale(scale); }}>
      <div className="graph-scroll-area" style={{ width: geometry.width * scale, height: geometry.height * scale }}>
        <div ref={canvas} className="graph-canvas" data-scale={scale} style={{ width: geometry.width, height: geometry.height, transform: `scale(${scale})` }}>
          <svg className="graph-connections" aria-hidden="true" width={geometry.width} height={geometry.height}>
            {graph.edges.map(edge => {
              const source = geometry.ports[edge.sourcePortId];
              const target = geometry.ports[edge.targetPortId];
              if (!source || !target) return null;
              return <path key={edge.id} data-edge-id={edge.id} d={connectionPath(source, target, geometry.boxes)}/>;
            })}
          </svg>
          {children}
        </div>
      </div>
    </div>
  </div>;
}

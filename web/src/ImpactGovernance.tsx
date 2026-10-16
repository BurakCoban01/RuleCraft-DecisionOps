import { useCallback, useEffect, useMemo, useState } from 'react';
import { api, ApiError } from './api';
import type { ImpactAssessmentDetail, ImpactAssessmentSummary, ImpactWorkspace } from './api';

const categories = ['ALL', 'UPSTREAM', 'MODEL', 'ASSURANCE', 'GOVERNANCE', 'RUNTIME'] as const;

export function ImpactGovernance({ projectId, serviceId, onBack }: { projectId: string; serviceId: string; onBack: () => void }) {
  const [workspace, setWorkspace] = useState<ImpactWorkspace | null>(null);
  const [history, setHistory] = useState<ImpactAssessmentSummary[] | null>(null);
  const [selected, setSelected] = useState<ImpactAssessmentDetail | null>(null);
  const [category, setCategory] = useState<(typeof categories)[number]>('ALL');
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState('');
  const [success, setSuccess] = useState('');
  const [stale, setStale] = useState(false);

  const load = useCallback(async () => {
    setError(''); setStale(false);
    try {
      const [impact, assessments] = await Promise.all([api.dependencyImpact(projectId, serviceId), api.impactAssessments(projectId, serviceId)]);
      setWorkspace(impact); setHistory(assessments); setSelected(null);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Dependency impact could not be loaded.');
      setWorkspace(null); setHistory([]);
    }
  }, [projectId, serviceId]);
  useEffect(() => { void load(); }, [load]);

  async function capture() {
    if (!workspace) return;
    setBusy('capture'); setError(''); setSuccess(''); setStale(false);
    try {
      const detail = await api.captureImpactAssessment(projectId, serviceId, workspace.impact.sourceFingerprint);
      setSelected(detail); setHistory(current => [detail.assessment, ...(current ?? []).filter(item => item.id !== detail.assessment.id)]);
      setSuccess('Immutable impact assessment captured.');
    } catch (reason) {
      if (reason instanceof ApiError && reason.code === 'RC-IMPACT-STALE') setStale(true);
      setError(reason instanceof Error ? reason.message : 'Impact evidence could not be captured.');
    } finally { setBusy(''); }
  }

  async function openAssessment(id: string) {
    setBusy(`assessment-${id}`); setError('');
    try { setSelected(await api.impactAssessment(projectId, serviceId, id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Assessment detail could not be loaded.'); }
    finally { setBusy(''); }
  }

  const impact = selected?.impact ?? workspace?.impact ?? null;
  const filtered = useMemo(() => impact?.items.filter(item => {
    const matchesCategory = category === 'ALL' || item.category === category;
    const search = query.trim().toLowerCase();
    return matchesCategory && (!search || `${item.label} ${item.detail} ${item.kind} ${item.status}`.toLowerCase().includes(search));
  }) ?? [], [impact, category, query]);

  function exportEvidence() {
    if (!impact) return;
    const value = selected ?? { mode: 'LIVE', impact };
    const url = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url;
    link.download = `dependency-impact-${impact.serviceKey.toLowerCase()}-${selected ? selected.assessment.id : 'live'}.json`;
    link.click(); URL.revokeObjectURL(url);
  }

  if (!workspace || history === null || !impact) return <section className="workspace-page impact-state"><button className="text-button" onClick={onBack}>← Back to decision graph</button><p className="eyebrow">Dependency governance</p><h1>{error ? 'Impact analysis is unavailable' : 'Mapping governed dependencies…'}</h1>{error ? <><p>{error}</p><button className="secondary-button" onClick={() => void load()}>Retry impact analysis</button></> : <div className="skeleton-lines"><span/><span/><span/></div>}</section>;

  return <section className="workspace-page impact-workbench"><button className="text-button" onClick={onBack}>← Back to decision graph</button>
    <header className="impact-heading"><div><p className="eyebrow">{impact.serviceKey} · Dependency governance</p><h1>Impact analysis</h1><p>Trace upstream types and model flow into scenarios, review evidence, promotion, and the active runtime before changing the governed baseline.</p></div><div className="impact-heading-actions"><span className={`impact-risk ${impact.riskLevel.toLowerCase()}`}>{impact.riskLevel}</span><button className="secondary-button" onClick={exportEvidence}>Export evidence</button>{workspace.canAssess && <button className="primary-button" disabled={busy === 'capture' || Boolean(selected)} onClick={() => void capture()}>{busy === 'capture' ? 'Capturing…' : 'Capture assessment'}</button>}</div></header>
    {!workspace.canAssess && <div className="permission-banner">Read-only governance access. Authors and reviewers can capture immutable assessments.</div>}
    {selected && <div className="impact-snapshot-banner"><div><strong>Immutable assessment</strong><span>{selected.assessment.capturedByName} · {new Date(selected.assessment.capturedAt).toLocaleString()} · {selected.assessment.snapshotFingerprint.slice(0,12)}</span></div><button className="secondary-button" onClick={() => setSelected(null)}>Return to live model</button></div>}
    {error && <div className="inline-alert" role="alert">{error}{stale ? <button className="text-button" onClick={() => void load()}>Refresh changed dependencies</button> : <button className="text-button" onClick={() => void load()}>Retry</button>}</div>}{success && <div className="success-banner" role="status">{success}</div>}
    <div className="impact-scorecard"><div><span>Readiness</span><strong className={impact.readyForReview ? 'low' : 'high'}>{impact.readyForReview ? 'REVIEWABLE' : 'ACTION NEEDED'}</strong></div><div><span>Dependencies</span><strong>{impact.summary.totalDependencies}</strong></div><div><span>Blockers</span><strong className={impact.summary.blockers ? 'high' : 'low'}>{impact.summary.blockers}</strong></div><div><span>Warnings</span><strong>{impact.summary.warnings}</strong></div><div><span>Source identity</span><code>{impact.sourceFingerprint.slice(0,12)}</code></div></div>
    <div className="impact-layout"><main><section className="impact-findings"><div className="impact-section-heading"><div><p className="eyebrow">Governance findings</p><h2>{impact.findings.length ? `${impact.findings.length} review signal${impact.findings.length === 1 ? '' : 's'}` : 'No governance findings'}</h2></div><span>{impact.workingValidationStatus} working model</span></div>{impact.findings.length === 0 ? <div className="impact-empty"><h3>Dependency evidence is aligned</h3><p>The working copy, assurance evidence, governed baseline, and runtime have no detected drift.</p></div> : <div className="impact-finding-list">{impact.findings.map(finding => <article key={`${finding.code}-${finding.referenceId}`} className={finding.severity.toLowerCase()}><span className="impact-risk">{finding.severity}</span><div><strong>{finding.title}</strong><p>{finding.detail}</p><small>{finding.action}</small></div><code>{finding.code}</code></article>)}</div>}</section>
      <section className="impact-dependencies"><div className="impact-section-heading"><div><p className="eyebrow">Blast radius</p><h2>Dependency inventory</h2></div><span>{filtered.length} of {impact.items.length}</span></div><div className="impact-toolbar"><nav aria-label="Dependency category">{categories.map(value => <button key={value} className={category === value ? 'current' : ''} onClick={() => setCategory(value)}>{value}</button>)}</nav><label>Search dependencies<input value={query} maxLength={120} onChange={event => setQuery(event.target.value)} placeholder="Type, node, scenario, version or status"/></label></div>{filtered.length === 0 ? <div className="impact-empty"><h3>No dependencies match</h3><p>Choose another category or clear the search.</p><button className="secondary-button" onClick={() => { setCategory('ALL'); setQuery(''); }}>Clear filters</button></div> : <div className="impact-item-list">{filtered.map(item => <article key={`${item.kind}-${item.referenceId}`}><span className={`impact-risk ${item.risk.toLowerCase()}`}>{item.risk}</span><div><small>{item.category} · {item.kind.replaceAll('_', ' ')}</small><strong>{item.label}</strong><p>{item.detail}</p></div><b>{item.status}</b></article>)}</div>}</section></main>
      <aside className="impact-history"><div className="impact-section-heading"><div><p className="eyebrow">Immutable evidence</p><h2>Assessments</h2></div><span>{history.length}</span></div>{history.length === 0 ? <div className="impact-empty compact"><h3>No snapshots</h3><p>Capture the live dependency state before review or promotion.</p></div> : history.map(item => <button key={item.id} className={selected?.assessment.id === item.id ? 'selected' : ''} disabled={busy === `assessment-${item.id}`} onClick={() => void openAssessment(item.id)}><span className={`impact-risk ${item.riskLevel.toLowerCase()}`}>{item.riskLevel}</span><strong>{item.dependencyCount} dependencies</strong><small>{item.blockerCount} blockers · {item.capturedByName}<br/>{new Date(item.capturedAt).toLocaleString()}</small><code>{item.snapshotFingerprint.slice(0,12)}</code></button>)}</aside></div>
  </section>;
}

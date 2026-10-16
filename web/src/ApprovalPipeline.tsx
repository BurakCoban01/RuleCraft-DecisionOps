import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api } from './api';
import type { ApprovalWorkflow, ChangeRequestDetail, ChangeRequestView, DecisionService, DecisionVersionSummary } from './api';

type Action = 'request-changes' | 'approve' | 'reject' | 'promote';

export function ApprovalPipeline({ projectId, service, versions, canEdit }: { projectId: string; service: DecisionService; versions: DecisionVersionSummary[]; canEdit: boolean }) {
  const [workflow, setWorkflow] = useState<ApprovalWorkflow | null>(null);
  const [detail, setDetail] = useState<ChangeRequestDetail | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [action, setAction] = useState<Action | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const load = useCallback(async () => {
    setError('');
    try {
      const next = await api.approvalWorkflow(projectId, service.id);
      setWorkflow(next);
      if (next.requests.length > 0) setDetail(await api.changeRequestDetail(projectId, service.id, next.requests[0].id));
      else setDetail(null);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'Approval workflow could not be loaded.'); setWorkflow({ requests: [], promotion: null, accessRole: 'VIEWER' }); }
  }, [projectId, service.id]);
  useEffect(() => { void load(); }, [load]);

  const latest = versions[0];
  const canReview = workflow?.accessRole === 'ADMIN' || workflow?.accessRole === 'OWNER' || workflow?.accessRole === 'REVIEWER';
  const canPromote = workflow?.accessRole === 'ADMIN' || workflow?.accessRole === 'OWNER';
  const canCreate = canEdit && service.status === 'ACTIVE' && latest?.validationStatus === 'VALID' && !workflow?.requests.some(item => item.decisionVersionId === latest.id);

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!latest) return; setSaving(true); setError(''); setSuccess('');
    const data = new FormData(event.currentTarget);
    try {
      const created = await api.createChangeRequest(projectId, service.id, { decisionVersionId: latest.id, title: String(data.get('title')), summary: String(data.get('summary')) });
      setCreateOpen(false); await load(); setSuccess(`CR-${created.requestNumber} opened for V${created.decisionVersionNumber}.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'The change request could not be opened.'); }
    finally { setSaving(false); }
  }
  async function submit(request: ChangeRequestView) {
    setSaving(true); setError(''); setSuccess('');
    try { const updated = await api.transitionChangeRequest(projectId, service.id, request.id, 'submit', request.version, 'Submitted with captured validation evidence'); await load(); setSuccess(`CR-${updated.requestNumber} submitted for review.`); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'The request could not be submitted.'); }
    finally { setSaving(false); }
  }
  async function transition(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!detail || !action) return; setSaving(true); setError(''); setSuccess('');
    const note = String(new FormData(event.currentTarget).get('note'));
    try {
      const updated = await api.transitionChangeRequest(projectId, service.id, detail.request.id, action, detail.request.version, note);
      setAction(null); await load(); setSuccess(action === 'promote' ? `V${updated.decisionVersionNumber} is the governed baseline.` : `CR-${updated.requestNumber} moved to ${updated.state.toLowerCase().replace('_', ' ')}.`);
    } catch (reason) { setError(reason instanceof Error ? reason.message : 'The workflow transition could not be completed.'); }
    finally { setSaving(false); }
  }
  async function select(request: ChangeRequestView) {
    setError(''); setAction(null);
    try { setDetail(await api.changeRequestDetail(projectId, service.id, request.id)); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'The review timeline could not be loaded.'); }
  }

  if (workflow === null) return <section className="approval-pipeline pipeline-loading"><p className="eyebrow">Promotion pipeline</p><h2>Loading governed review…</h2><div className="skeleton-lines"><span/><span/></div></section>;
  return <section className="approval-pipeline" aria-labelledby="approval-title">
    <header className="pipeline-heading"><div><p className="eyebrow">Governed change workflow</p><h2 id="approval-title">Approval &amp; promotion</h2></div>{canCreate && <button className="primary-button" onClick={() => setCreateOpen(!createOpen)}>{createOpen ? 'Close request' : 'Open change request'}</button>}</header>
    {workflow.promotion && <div className="promotion-banner"><span>Current governed baseline</span><strong>V{workflow.promotion.decisionVersionNumber} · {workflow.promotion.decisionVersionLabel}</strong><small>Promoted by {workflow.promotion.promotedByName} · {new Date(workflow.promotion.promotedAt).toLocaleString()}</small></div>}
    {service.status === 'ARCHIVED' && <div className="permission-banner">This service is archived. Its review history remains visible, but transitions are disabled.</div>}
    {!canEdit && !canReview && <div className="permission-banner">Read-only governance access. Editors submit changes, reviewers decide, and owners promote.</div>}
    {latest?.validationStatus === 'INVALID' && <div className="inline-alert">The latest version is invalid and cannot enter approval. Capture a valid version after resolving its issues.</div>}
    {error && <div className="inline-alert" role="alert">{error}<button className="text-button" onClick={() => void load()}>Retry</button></div>}{success && <div className="success-banner" role="status">{success}</div>}
    {createOpen && latest && <form className="change-request-form" onSubmit={create}><div><span className="version-number">LATEST · V{latest.versionNumber}</span><h3>Open a review package</h3><p>The candidate is immutable; fixes require a new captured version.</p></div><label>Request title<input name="title" required maxLength={160} placeholder="Approve eligibility threshold"/></label><label>Change summary<textarea name="summary" maxLength={2000} rows={3} placeholder="Risk, intent, and evidence for reviewers"/></label><button className="primary-button" disabled={saving}>{saving ? 'Opening…' : 'Create draft'}</button></form>}
    {workflow.requests.length === 0 ? <div className="pipeline-empty"><span>01 → 04</span><h3>No change requests yet</h3><p>{versions.length === 0 ? 'Capture a valid decision version first.' : latest?.validationStatus === 'VALID' ? 'Open a draft from the latest captured version.' : 'Resolve validation issues and capture a valid candidate.'}</p></div> : <div className="pipeline-layout"><aside className="request-list"><p className="eyebrow">Request history</p>{workflow.requests.map(request => <button key={request.id} className={detail?.request.id === request.id ? 'selected' : ''} onClick={() => void select(request)}><span>CR-{request.requestNumber} · V{request.decisionVersionNumber}</span><strong>{request.title}</strong><small>{request.state.replace('_', ' ')}{request.stale ? ' · STALE' : ''}</small></button>)}</aside>{detail && <section className="request-detail"><div className="request-title"><div><span className={`workflow-state ${detail.request.state.toLowerCase()}`}>{detail.request.state.replace('_', ' ')}</span><h3>CR-{detail.request.requestNumber} · {detail.request.title}</h3><p>{detail.request.summary || 'No change summary was provided.'}</p></div><div><span>Candidate</span><strong>V{detail.request.decisionVersionNumber}</strong></div></div>
      <div className="pipeline-stages" aria-label="Promotion stages"><span className="done">Draft</span><span className={['SUBMITTED','APPROVED','PROMOTED'].includes(detail.request.state) ? 'done' : ''}>Review</span><span className={['APPROVED','PROMOTED'].includes(detail.request.state) ? 'done' : ''}>Approved</span><span className={detail.request.state === 'PROMOTED' ? 'done' : ''}>Promoted</span></div>
      {detail.request.stale && <div className="inline-alert">A newer captured version exists. This request remains auditable but cannot be approved or promoted.</div>}
      {service.status === 'ACTIVE' && !detail.request.stale && <div className="workflow-actions">{detail.request.state === 'DRAFT' && canEdit && <button className="primary-button" disabled={saving} onClick={() => void submit(detail.request)}>Submit for review</button>}{detail.request.state === 'SUBMITTED' && canReview && <><button className="primary-button" onClick={() => setAction('approve')}>Approve</button><button className="secondary-button" onClick={() => setAction('request-changes')}>Request changes</button><button className="text-button danger-action" onClick={() => setAction('reject')}>Reject</button></>}{detail.request.state === 'APPROVED' && canPromote && <button className="primary-button" onClick={() => setAction('promote')}>Promote baseline</button>}</div>}
      {action && <form className="workflow-note" onSubmit={transition}><div><p className="eyebrow">{action.replace('-', ' ')}</p><h3>{action === 'promote' ? 'Confirm governed baseline' : 'Record review decision'}</h3></div><label>Decision note<textarea name="note" required={action === 'request-changes' || action === 'reject'} maxLength={2000} rows={3} placeholder="Explain the evidence behind this decision"/></label><button className="primary-button" disabled={saving}>{saving ? 'Recording…' : 'Confirm action'}</button><button type="button" className="text-button" onClick={() => setAction(null)}>Cancel</button></form>}
      <section className="workflow-timeline"><p className="eyebrow">Immutable timeline</p>{detail.events.map(event => <article key={event.id}><i aria-hidden="true"/><div><strong>{event.eventType.replace('_', ' ')}</strong><p>{event.note || `${event.fromState || 'START'} → ${event.toState}`}</p><small>{event.actorName} · {new Date(event.createdAt).toLocaleString()}</small></div></article>)}</section>
    </section>}</div>}
  </section>;
}

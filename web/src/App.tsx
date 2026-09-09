import { useCallback, useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { api, ApiError } from './api';
import type { Project, ProjectMember, Session, WorkspaceUser } from './api';
import { usePlatformManifest } from './usePlatformManifest';

type Theme = 'light' | 'dark';
type View = 'projects' | 'team';

function BrandMark() {
  return <svg className="brand-mark" viewBox="0 0 40 40" aria-hidden="true"><path d="M7 8.5h14.5L33 20 21.5 31.5H7V8.5Z"/><path d="M13 14h8l6 6-6 6h-8V14Z"/><circle cx="18" cy="20" r="2.25"/></svg>;
}

function ThemeIcon({ theme }: { theme: Theme }) {
  return theme === 'light'
    ? <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3v2m0 14v2m9-9h-2M5 12H3m15.36-6.36-1.42 1.42M7.06 16.94l-1.42 1.42m12.72 0-1.42-1.42M7.06 7.06 5.64 5.64M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z"/></svg>
    : <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 15.2A8 8 0 0 1 8.8 4 8 8 0 1 0 20 15.2Z"/></svg>;
}

function LoadingState({ label = 'Opening workspace' }: { label?: string }) {
  return <section className="state-panel compact-state" aria-live="polite" aria-busy="true"><div className="state-symbol loading-symbol"><span/></div><p className="eyebrow">{label}</p><h1>Preparing your governed decision environment</h1><div className="skeleton-lines"><span/><span/><span/></div></section>;
}

function UnavailableState({ offline, retry }: { offline: boolean; retry: () => void }) {
  return <section className="state-panel" role="alert"><div className="state-symbol error-symbol">!</div><p className="eyebrow">{offline ? 'Connection interrupted' : 'Workspace unavailable'}</p><h1>{offline ? 'Your workspace is offline' : 'The decision workspace cannot be reached'}</h1><p className="state-copy">Your work remains unchanged. Reconnect and try again when the service is available.</p><button className="primary-button" onClick={retry}>Try connection again</button></section>;
}

function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitting(true); setError('');
    const data = new FormData(event.currentTarget);
    try { onLogin(await api.login(String(data.get('workspace')), String(data.get('email')), String(data.get('password')))); }
    catch (reason) { setError(reason instanceof ApiError ? reason.message : 'Sign-in could not be completed.'); }
    finally { setSubmitting(false); }
  }
  return <section className="login-layout">
    <div className="login-intro"><div className="decision-line"><span/><span/><span/></div><p className="eyebrow">Decision operations</p><h1>Enter your governed workspace</h1><p className="lede">Author, review, and operate business decisions within the controls assigned to your role.</p><dl className="trust-list"><div><dt>Tenant isolated</dt><dd>Workspace context is established from your verified session.</dd></div><div><dt>Audited changes</dt><dd>Administrative and project actions leave a durable trail.</dd></div></dl></div>
    <form className="login-card" onSubmit={submit}><p className="eyebrow">Private access</p><h2>Sign in to RuleCraft</h2><p>Use the workspace handle supplied by your administrator.</p>{error && <div className="inline-alert" role="alert">{error}</div>}<label>Workspace<input name="workspace" autoComplete="organization" required pattern="[a-z0-9][a-z0-9\-]{1,62}[a-z0-9]" placeholder="acme-rules"/></label><label>Email<input name="email" type="email" autoComplete="username" required placeholder="you@company.com"/></label><label>Password<input name="password" type="password" autoComplete="current-password" required/></label><button className="primary-button full-button" disabled={submitting}>{submitting ? 'Verifying…' : 'Continue to workspace'}</button></form>
  </section>;
}

function ProjectsView({ user }: { user: NonNullable<Session['user']> }) {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [selected, setSelected] = useState<Project | null>(null);
  const [members, setMembers] = useState<ProjectMember[] | null>(null);
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const load = useCallback(() => api.projects().then(setProjects).catch((e) => { setError(e.message); setProjects([]); }), []);
  useEffect(() => { void load(); }, [load]);
  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); const form = event.currentTarget; const data = new FormData(form);
    try { const project = await api.createProject({ projectKey: String(data.get('projectKey')), name: String(data.get('name')), description: String(data.get('description')) }); setProjects((current) => [...(current ?? []), project].sort((a, b) => a.name.localeCompare(b.name))); setCreating(false); form.reset(); }
    catch (reason) { setError(reason instanceof Error ? reason.message : 'Project creation failed.'); }
  }
  async function open(project: Project) { setSelected(project); setMembers(null); setError(''); try { setMembers(await api.projectMembers(project.id)); } catch (e) { setError(e instanceof Error ? e.message : 'Members could not be loaded.'); } }
  if (projects === null) return <LoadingState label="Loading project access"/>;
  if (selected) return <ProjectDetail project={selected} members={members} user={user} onBack={() => setSelected(null)} onMembers={setMembers} error={error}/>;
  return <section className="workspace-page" aria-labelledby="projects-title"><header className="page-heading"><div><p className="eyebrow">Decision catalog</p><h1 id="projects-title">Projects</h1><p>Bounded workspaces for decision models, access, and governance.</p></div><button className="primary-button" onClick={() => setCreating(!creating)}>{creating ? 'Close form' : 'New project'}</button></header>{error && <div className="inline-alert" role="alert">{error}</div>}
    {creating && <form className="editor-panel three-fields" onSubmit={create}><label>Project key<input name="projectKey" required pattern="[A-Za-z][A-Za-z0-9\-]{1,23}" placeholder="PRICING"/></label><label>Project name<input name="name" required maxLength={160} placeholder="Pricing decisions"/></label><label>Description<input name="description" maxLength={600} placeholder="Scope and operating purpose"/></label><button className="primary-button">Create project</button></form>}
    {projects.length === 0 ? <div className="empty-state"><span>01</span><h2>Create the first governed project</h2><p>Projects establish access boundaries before decision models are authored.</p></div> : <div className="project-grid">{projects.map((project) => <button className="project-card" key={project.id} onClick={() => open(project)}><span className="project-key">{project.projectKey}</span><h2>{project.name}</h2><p>{project.description || 'No description has been added.'}</p><span className="role-badge">{project.accessRole}</span><span className="open-label">Open project <span aria-hidden="true">→</span></span></button>)}</div>}
  </section>;
}

function ProjectDetail({ project, members, user, onBack, onMembers, error }: { project: Project; members: ProjectMember[] | null; user: NonNullable<Session['user']>; onBack: () => void; onMembers: (members: ProjectMember[]) => void; error: string }) {
  const [users, setUsers] = useState<WorkspaceUser[]>([]); const [localError, setLocalError] = useState('');
  const canManage = user.tenantRole === 'ADMIN' || project.accessRole === 'OWNER';
  useEffect(() => { if (canManage && user.tenantRole === 'ADMIN') api.users().then(setUsers).catch(() => undefined); }, [canManage, user.tenantRole]);
  async function add(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const data = new FormData(event.currentTarget); try { const member = await api.upsertMember(project.id, String(data.get('userId')), String(data.get('role')) as ProjectMember['role']); onMembers([...(members ?? []).filter((m) => m.userId !== member.userId), member]); } catch (e) { setLocalError(e instanceof Error ? e.message : 'Membership could not be changed.'); } }
  return <section className="workspace-page"><button className="text-button" onClick={onBack}>← Back to projects</button><header className="project-hero"><div><span className="project-key">{project.projectKey}</span><h1>{project.name}</h1><p>{project.description || 'No description has been added.'}</p></div><span className="role-badge large">{project.accessRole} access</span></header>{(error || localError) && <div className="inline-alert" role="alert">{error || localError}</div>}
    <div className="section-heading"><div><p className="eyebrow">Access boundary</p><h2>Project members</h2></div><span>{members?.length ?? '—'} assigned</span></div>
    {canManage && users.length > 0 && <form className="editor-panel membership-form" onSubmit={add}><label>Workspace user<select name="userId">{users.filter((candidate) => candidate.status === 'ACTIVE').map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.displayName} · {candidate.email}</option>)}</select></label><label>Project role<select name="role"><option>EDITOR</option><option>REVIEWER</option><option>VIEWER</option><option>OWNER</option></select></label><button className="secondary-button">Assign access</button></form>}
    {members === null ? <div className="table-loading">Loading members…</div> : <div className="data-table" role="table" aria-label="Project members"><div className="table-row table-head" role="row"><span>Name</span><span>Email</span><span>Role</span></div>{members.map((member) => <div className="table-row" role="row" key={member.userId}><strong>{member.displayName}</strong><span>{member.email}</span><span className="role-badge">{member.role}</span></div>)}</div>}
  </section>;
}

function TeamView({ currentUser }: { currentUser: NonNullable<Session['user']> }) {
  const [users, setUsers] = useState<WorkspaceUser[] | null>(null); const [error, setError] = useState(''); const [adding, setAdding] = useState(false);
  const load = useCallback(() => api.users().then(setUsers).catch((e) => setError(e.message)), []); useEffect(() => { void load(); }, [load]);
  async function create(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const data = new FormData(form); try { const created = await api.createUser({ email: String(data.get('email')), displayName: String(data.get('displayName')), tenantRole: String(data.get('tenantRole')), temporaryPassword: String(data.get('temporaryPassword')) }); setUsers((all) => [...(all ?? []), created]); form.reset(); setAdding(false); } catch (e) { setError(e instanceof Error ? e.message : 'User could not be created.'); } }
  async function toggle(user: WorkspaceUser) { try { const changed = await api.changeUserStatus(user.id, user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'); setUsers((all) => (all ?? []).map((item) => item.id === changed.id ? changed : item)); } catch (e) { setError(e instanceof Error ? e.message : 'Status could not be changed.'); } }
  if (users === null) return <LoadingState label="Loading workspace team"/>;
  return <section className="workspace-page"><header className="page-heading"><div><p className="eyebrow">Workspace administration</p><h1>Team access</h1><p>Provision identities and control workspace-level access.</p></div><button className="primary-button" onClick={() => setAdding(!adding)}>{adding ? 'Close form' : 'Add user'}</button></header>{error && <div className="inline-alert" role="alert">{error}</div>}
    {adding && <form className="editor-panel user-form" onSubmit={create}><label>Display name<input name="displayName" required maxLength={160}/></label><label>Email<input name="email" type="email" required/></label><label>Workspace role<select name="tenantRole"><option value="MEMBER">Member</option><option value="ADMIN">Administrator</option></select></label><label>Temporary password<input name="temporaryPassword" type="password" required minLength={12} autoComplete="new-password"/></label><button className="primary-button">Provision user</button></form>}
    <div className="data-table users-table" role="table" aria-label="Workspace users"><div className="table-row table-head"><span>Person</span><span>Workspace role</span><span>Status</span><span>Action</span></div>{users.map((person) => <div className="table-row" key={person.id}><span><strong>{person.displayName}</strong><small>{person.email}</small></span><span>{person.tenantRole}</span><span className={`status-pill ${person.status.toLowerCase()}`}>{person.status}</span><button className="text-button" disabled={person.id === currentUser.id} onClick={() => toggle(person)}>{person.status === 'ACTIVE' ? 'Disable' : 'Restore'}</button></div>)}</div>
  </section>;
}

function AuthenticatedShell({ session, onLogout, theme, toggleTheme }: { session: Session; onLogout: () => void; theme: Theme; toggleTheme: () => void }) {
  const user = session.user!; const requestedTeam = location.pathname === '/admin/users';
  const [view, setViewState] = useState<View>(requestedTeam && user.tenantRole === 'ADMIN' ? 'team' : 'projects');
  const [permissionNotice] = useState(requestedTeam && user.tenantRole !== 'ADMIN');
  function setView(next: View) { history.pushState({}, '', next === 'team' ? '/admin/users' : '/projects'); setViewState(next); }
  return <div className="app-shell"><header className="mobile-header"><span className="brand"><BrandMark/><span>RuleCraft</span></span><button className="icon-button" onClick={toggleTheme} aria-label={`Use ${theme === 'light' ? 'dark' : 'light'} theme`}><ThemeIcon theme={theme}/></button><nav className="mobile-nav" aria-label="Mobile navigation"><button className={view === 'projects' ? 'current' : ''} onClick={() => setView('projects')}>Projects</button>{user.tenantRole === 'ADMIN' && <button className={view === 'team' ? 'current' : ''} onClick={() => setView('team')}>Team access</button>}<button onClick={onLogout}>Sign out</button></nav></header><aside className="navigation-rail"><a className="brand" href="/projects"><BrandMark/><span>RuleCraft</span></a><div className="workspace-identity"><span>{user.workspace}</span><strong>{user.displayName}</strong></div><nav aria-label="Primary navigation"><button className={`nav-item ${view === 'projects' ? 'current' : ''}`} onClick={() => setView('projects')}><span className="nav-glyph">P</span><span>Projects</span></button>{user.tenantRole === 'ADMIN' && <button className={`nav-item ${view === 'team' ? 'current' : ''}`} onClick={() => setView('team')}><span className="nav-glyph">A</span><span>Team access</span></button>}</nav><div className="rail-footer"><span className="private-label"><span/>Private environment</span><button className="theme-button" onClick={toggleTheme}><ThemeIcon theme={theme}/><span>Use {theme === 'light' ? 'dark' : 'light'} theme</span></button><button className="signout-button" onClick={onLogout}>Sign out</button></div></aside><main><div className="page-context"><span>{user.workspace}</span><span>/</span><strong>{view === 'projects' ? 'Projects' : 'Team access'}</strong></div>{permissionNotice && <div className="inline-alert permission" role="alert">You do not have permission to open workspace administration.</div>}{view === 'projects' ? <ProjectsView user={user}/> : <TeamView currentUser={user}/>}</main><footer className="product-footer"><span>RuleCraft DecisionOps</span><span>{user.email}</span></footer></div>;
}

export function App() {
  const { state: platform, refresh } = usePlatformManifest(); const [session, setSession] = useState<Session | null>(null); const [sessionError, setSessionError] = useState(false);
  const [theme, setTheme] = useState<Theme>(() => { const stored = localStorage.getItem('rulecraft-theme'); return stored === 'light' || stored === 'dark' ? stored : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'; });
  useEffect(() => { document.documentElement.dataset.theme = theme; localStorage.setItem('rulecraft-theme', theme); }, [theme]);
  useEffect(() => { if (platform.status === 'ready') api.session().then(setSession).catch(() => setSessionError(true)); }, [platform.status]);
  async function logout() { try { await api.logout(); } finally { history.replaceState({}, '', '/'); setSession({ authenticated: false, user: null }); } }
  const toggleTheme = () => setTheme(theme === 'light' ? 'dark' : 'light');
  if (platform.status === 'loading' || (platform.status === 'ready' && session === null && !sessionError)) return <div className="public-shell"><PublicHeader theme={theme} toggle={toggleTheme}/><LoadingState/></div>;
  if (platform.status === 'offline' || platform.status === 'error' || sessionError) return <div className="public-shell"><PublicHeader theme={theme} toggle={toggleTheme}/><UnavailableState offline={platform.status === 'offline'} retry={() => { setSessionError(false); refresh(); }}/></div>;
  if (!session?.authenticated) return <div className="public-shell"><PublicHeader theme={theme} toggle={toggleTheme}/><main><Login onLogin={setSession}/></main><footer className="product-footer"><span>RuleCraft DecisionOps</span><span>Private workspace</span></footer></div>;
  return <AuthenticatedShell session={session} onLogout={logout} theme={theme} toggleTheme={toggleTheme}/>;
}

function PublicHeader({ theme, toggle }: { theme: Theme; toggle: () => void }) { return <header className="public-header"><span className="brand"><BrandMark/><span>RuleCraft</span></span><span className="private-label"><span/>Private environment</span><button className="icon-button" onClick={toggle} aria-label={`Use ${theme === 'light' ? 'dark' : 'light'} theme`}><ThemeIcon theme={theme}/></button></header>; }

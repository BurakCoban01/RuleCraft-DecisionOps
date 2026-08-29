export interface SessionUser {
  id: string;
  tenantId: string;
  workspace: string;
  displayName: string;
  email: string;
  tenantRole: 'ADMIN' | 'MEMBER';
}

export interface Session { authenticated: boolean; user: SessionUser | null }
export interface Project { id: string; projectKey: string; name: string; description: string | null; accessRole: string; createdAt: string; version: number }
export interface WorkspaceUser { id: string; email: string; displayName: string; tenantRole: 'ADMIN' | 'MEMBER'; status: 'ACTIVE' | 'DISABLED'; version: number }
export interface ProjectMember { userId: string; displayName: string; email: string; role: 'OWNER' | 'EDITOR' | 'REVIEWER' | 'VIEWER' }

export class ApiError extends Error {
  constructor(message: string, readonly status: number, readonly code?: string, readonly fields?: Record<string, string>) {
    super(message);
  }
}

function csrfToken() {
  return document.cookie.split('; ').find((part) => part.startsWith('XSRF-TOKEN='))?.split('=').slice(1).join('=');
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers);
  headers.set('Accept', 'application/json');
  if (init.body) headers.set('Content-Type', 'application/json');
  const token = csrfToken();
  if (token && init.method && init.method !== 'GET') headers.set('X-XSRF-TOKEN', decodeURIComponent(token));
  const response = await fetch(path, { ...init, headers, credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const problem = await response.json().catch(() => ({})) as { detail?: string; code?: string; fields?: Record<string, string> };
    throw new ApiError(problem.detail ?? 'The request could not be completed.', response.status, problem.code, problem.fields);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export const api = {
  session: () => request<Session>('/api/v1/session'),
  login: (workspace: string, email: string, password: string) => request<Session>('/api/v1/session/login', { method: 'POST', body: JSON.stringify({ workspace, email, password }) }),
  logout: () => request<void>('/api/v1/session', { method: 'DELETE' }),
  projects: () => request<Project[]>('/api/v1/projects'),
  createProject: (body: { projectKey: string; name: string; description: string }) => request<Project>('/api/v1/projects', { method: 'POST', body: JSON.stringify(body) }),
  projectMembers: (projectId: string) => request<ProjectMember[]>(`/api/v1/projects/${projectId}/members`),
  upsertMember: (projectId: string, userId: string, role: ProjectMember['role']) => request<ProjectMember>(`/api/v1/projects/${projectId}/members/${userId}`, { method: 'PUT', body: JSON.stringify({ role }) }),
  removeMember: (projectId: string, userId: string) => request<void>(`/api/v1/projects/${projectId}/members/${userId}`, { method: 'DELETE' }),
  users: () => request<WorkspaceUser[]>('/api/v1/users'),
  createUser: (body: { email: string; displayName: string; tenantRole: string; temporaryPassword: string }) => request<WorkspaceUser>('/api/v1/users', { method: 'POST', body: JSON.stringify(body) }),
  changeUserStatus: (id: string, status: WorkspaceUser['status']) => request<WorkspaceUser>(`/api/v1/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
};

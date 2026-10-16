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
export type BuiltinType = 'TEXT' | 'NUMBER' | 'BOOLEAN' | 'DATE' | 'DATE_TIME';
export type TypeKind = 'OBJECT' | 'ENUM' | 'LIST';
export interface TypeRef { builtinType: BuiltinType | null; customTypeId: string | null; customTypeKey?: string | null; displayName?: string | null }
export interface TypedField { fieldKey: string; label: string; type: TypeRef; required: boolean }
export interface TypeDefinition { id: string; typeKey: string; name: string; description: string | null; kind: TypeKind; itemType: TypeRef | null; fields: TypedField[]; values: { value: string; label: string }[]; updatedAt: string; version: number }
export interface DecisionService { id: string; serviceKey: string; name: string; description: string | null; status: 'ACTIVE' | 'ARCHIVED'; inputs: TypedField[]; outputs: TypedField[]; updatedAt: string; version: number }

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
  types: (projectId: string) => request<TypeDefinition[]>(`/api/v1/projects/${projectId}/types`),
  createType: (projectId: string, body: unknown) => request<TypeDefinition>(`/api/v1/projects/${projectId}/types`, { method: 'POST', body: JSON.stringify(body) }),
  updateType: (projectId: string, typeId: string, body: unknown) => request<TypeDefinition>(`/api/v1/projects/${projectId}/types/${typeId}`, { method: 'PUT', body: JSON.stringify(body) }),
  deleteType: (projectId: string, typeId: string, version: number) => request<void>(`/api/v1/projects/${projectId}/types/${typeId}?version=${version}`, { method: 'DELETE' }),
  decisionServices: (projectId: string) => request<DecisionService[]>(`/api/v1/projects/${projectId}/decision-services`),
  createDecisionService: (projectId: string, body: unknown) => request<DecisionService>(`/api/v1/projects/${projectId}/decision-services`, { method: 'POST', body: JSON.stringify(body) }),
  updateDecisionService: (projectId: string, serviceId: string, body: unknown) => request<DecisionService>(`/api/v1/projects/${projectId}/decision-services/${serviceId}`, { method: 'PUT', body: JSON.stringify(body) }),
  changeDecisionServiceStatus: (projectId: string, serviceId: string, status: DecisionService['status'], version: number) => request<DecisionService>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/status`, { method: 'PATCH', body: JSON.stringify({ status, version }) }),
  users: () => request<WorkspaceUser[]>('/api/v1/users'),
  createUser: (body: { email: string; displayName: string; tenantRole: string; temporaryPassword: string }) => request<WorkspaceUser>('/api/v1/users', { method: 'POST', body: JSON.stringify(body) }),
  changeUserStatus: (id: string, status: WorkspaceUser['status']) => request<WorkspaceUser>(`/api/v1/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
};

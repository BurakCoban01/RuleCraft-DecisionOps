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
export type GraphNodeKind = 'INPUT' | 'DECISION' | 'OUTPUT';
export type GraphPortDirection = 'INPUT' | 'OUTPUT';
export interface GraphPort { id: string; nodeId: string; direction: GraphPortDirection; portKey: string; label: string; type: TypeRef; required: boolean; position: number }
export interface GraphNode { id: string; nodeKey: string; name: string; kind: GraphNodeKind; serviceFieldKey: string | null; positionX: number; positionY: number; ports: GraphPort[] }
export interface GraphEdge { id: string; sourcePortId: string; targetPortId: string }
export interface GraphIssue { severity: 'ERROR'; code: string; message: string; nodeId: string | null; portId: string | null }
export interface DecisionGraph { id: string; decisionServiceId: string; serviceKey: string; serviceName: string; validationStatus: 'VALID' | 'INVALID'; issues: GraphIssue[]; nodes: GraphNode[]; edges: GraphEdge[]; updatedAt: string; version: number }
export type HitPolicy = 'UNIQUE' | 'FIRST';
export type InputOperator = 'ANY' | 'EQUALS';
export interface TableColumn { portId: string; portKey: string; label: string; type: TypeRef; required: boolean }
export interface TableInputEntry { id: string; inputPortId: string; operator: InputOperator; valueJson: string | null }
export interface TableRule { id: string; enabled: boolean; description: string | null; inputs: TableInputEntry[]; outputValueJson: string | null }
export interface TableIssue { code: string; message: string; ruleId: string | null; portId: string | null }
export interface DecisionTable { id: string; graphId: string; nodeId: string; nodeKey: string; nodeName: string; hitPolicy: HitPolicy; validationStatus: 'VALID' | 'INVALID'; issues: TableIssue[]; inputs: TableColumn[]; output: TableColumn; rules: TableRule[]; updatedAt: string; version: number }
export interface DecisionTableSummary { nodeId: string; hitPolicy: HitPolicy; validationStatus: 'VALID' | 'INVALID'; enabledRules: number; updatedAt: string; version: number }
export interface ExpressionIssue { code: string; message: string; line: number; column: number }
export interface ExpressionSymbol { key: string; label: string; type: TypeRef }
export interface DecisionExpression { id: string; graphId: string; nodeId: string; nodeKey: string; nodeName: string; languageVersion: 'RCEL-1'; source: string; validationStatus: 'VALID' | 'INVALID'; issues: ExpressionIssue[]; inferredType: string | null; inputs: ExpressionSymbol[]; output: ExpressionSymbol; updatedAt: string; version: number }
export interface DecisionExpressionSummary { nodeId: string; validationStatus: 'VALID' | 'INVALID'; languageVersion: 'RCEL-1'; updatedAt: string; version: number }
export interface DecisionEvaluation { nodeId: string; nodeKey: string; nodeName: string; implementation: 'TABLE' | 'EXPRESSION'; implementationVersion: number; matchedRuleId: string | null; value: unknown }
export interface SandboxEvaluation { evaluationId: string; decisionServiceId: string; graphVersion: number; outputs: Record<string, unknown>; decisions: DecisionEvaluation[] }
export interface TestIssue { code: string; message: string; fieldKey: string | null; target: 'INPUT' | 'OUTPUT' | 'TEST' }
export interface TestRunSummary { id: string; status: 'PASSED' | 'FAILED'; graphVersion: number; executedAt: string }
export interface DecisionTestCase { id: string; testKey: string; name: string; description: string | null; inputs: Record<string, unknown>; expectedOutputs: Record<string, unknown>; validationStatus: 'VALID' | 'INVALID'; issues: TestIssue[]; latestRun: TestRunSummary | null; updatedAt: string; version: number }
export interface DecisionTestRun { id: string; testCaseId: string; testCaseVersion: number; evaluationId: string; graphVersion: number; status: 'PASSED' | 'FAILED'; expectedOutputs: Record<string, unknown>; actualOutputs: Record<string, unknown>; decisions: DecisionEvaluation[]; executedAt: string }
export type MatrixRunStatus = 'RUNNING' | 'COMPLETED' | 'INTERRUPTED';
export type MatrixCaseStatus = 'PASSED' | 'FAILED' | 'ERROR';
export interface MatrixRunSummary { id: string; clientRequestId: string; status: MatrixRunStatus; selectedCount: number; passedCount: number; failedCount: number; errorCount: number; startedAt: string; completedAt: string | null }
export interface MatrixCaseResult { id: string; position: number; testCaseId: string; testCaseVersion: number; testKey: string; testName: string; status: MatrixCaseStatus; testRunId: string | null; evaluationId: string | null; graphVersion: number | null; expectedOutputs: Record<string, unknown>; actualOutputs: Record<string, unknown> | null; decisions: DecisionEvaluation[]; errorCode: string | null; errorDetail: string | null; executedAt: string }
export interface RegressionMatrix { run: MatrixRunSummary; selectedCaseIds: string[]; results: MatrixCaseResult[] }
export interface DecisionVersionSummary { id: string; versionNumber: number; label: string; note: string | null; validationStatus: 'VALID' | 'INVALID'; fingerprint: string; sourceServiceVersion: number; sourceGraphVersion: number; capturedAt: string }
export interface WorkingDecisionState { serviceVersion: number; graphVersion: number; validationStatus: 'VALID' | 'INVALID'; fingerprint: string }
export type ChangeCategory = 'CONTRACT' | 'GRAPH' | 'IMPLEMENTATION' | 'TEST';
export type ChangeImpact = 'BREAKING' | 'BEHAVIORAL' | 'NON_BREAKING';
export interface SemanticChange { category: ChangeCategory; kind: 'ADDED' | 'REMOVED' | 'CHANGED'; impact: ChangeImpact; path: string; label: string; before: unknown | null; after: unknown | null }
export interface DecisionVersionDiff { base: DecisionVersionSummary; target: DecisionVersionSummary; changes: SemanticChange[]; breakingCount: number; behavioralCount: number; nonBreakingCount: number }
export type ChangeRequestState = 'DRAFT' | 'SUBMITTED' | 'CHANGES_REQUESTED' | 'APPROVED' | 'REJECTED' | 'PROMOTED';
export interface ChangeRequestView { id: string; requestNumber: number; decisionVersionId: string; decisionVersionNumber: number; decisionVersionLabel: string; validationStatus: 'VALID' | 'INVALID'; title: string; summary: string | null; state: ChangeRequestState; createdByName: string; createdAt: string; updatedAt: string; version: number; stale: boolean }
export interface ChangeEvent { id: string; eventType: string; fromState: ChangeRequestState | null; toState: ChangeRequestState; note: string | null; actorName: string; createdAt: string }
export interface PromotionView { changeRequestId: string; decisionVersionId: string; decisionVersionNumber: number; decisionVersionLabel: string; promotedByName: string; promotedAt: string; revision: number }
export interface ApprovalWorkflow { requests: ChangeRequestView[]; promotion: PromotionView | null; accessRole: string }
export interface ChangeRequestDetail { request: ChangeRequestView; events: ChangeEvent[] }
export type RuntimeDeploymentState = 'CREATED' | 'VALIDATING' | 'ACTIVE' | 'FAILED' | 'ROLLED_BACK' | 'RETIRED';
export interface RuntimeVersion { id: string; versionNumber: number; label: string; fingerprint: string }
export interface RuntimeEnvironment { id: string; environmentKey: string; name: string; activeDeploymentId: string | null; revision: number; updatedAt: string }
export interface RuntimeDeployment { id: string; sequenceNumber: number; decisionVersionId: string; decisionVersionNumber: number | null; decisionVersionLabel: string; changeRequestId: string; state: RuntimeDeploymentState; artifactFingerprint: string; failureCode: string | null; failureDetail: string | null; deployedByName: string; createdAt: string; updatedAt: string; revision: number }
export interface RuntimeDeploymentEvent { id: string; eventType: string; fromState: RuntimeDeploymentState | null; toState: RuntimeDeploymentState; actorName: string; note: string | null; createdAt: string }
export interface RuntimeSandbox { environment: RuntimeEnvironment | null; deployments: RuntimeDeployment[]; promotedVersion: RuntimeVersion | null; promotionRevision: number | null; accessRole: string }
export interface RuntimeDeploymentDetail { deployment: RuntimeDeployment; events: RuntimeDeploymentEvent[] }
export interface RuntimeEvaluation { evaluationId: string; deploymentId: string; decisionVersionId: string; artifactFingerprint: string; outputs: Record<string, unknown>; decisions: { nodeKey: string; nodeName: string; implementation: 'TABLE' | 'EXPRESSION'; matchedRuleId: string | null; value: unknown }[] }

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
  decisionGraph: (projectId: string, serviceId: string) => request<DecisionGraph | null>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph`).then((value) => value ?? null),
  initializeDecisionGraph: (projectId: string, serviceId: string) => request<DecisionGraph>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph`, { method: 'POST' }),
  updateDecisionGraph: (projectId: string, serviceId: string, body: { version: number; nodes: GraphNode[]; edges: GraphEdge[] }) => request<DecisionGraph>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph`, { method: 'PUT', body: JSON.stringify(body) }),
  synchronizeDecisionGraph: (projectId: string, serviceId: string, version: number) => request<DecisionGraph>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/contract-sync`, { method: 'POST', body: JSON.stringify({ version }) }),
  decisionTableSummaries: (projectId: string, serviceId: string) => request<DecisionTableSummary[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/decision-tables`),
  decisionTable: (projectId: string, serviceId: string, nodeId: string) => request<DecisionTable | null>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-table`).then((value) => value ?? null),
  initializeDecisionTable: (projectId: string, serviceId: string, nodeId: string) => request<DecisionTable>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-table`, { method: 'POST' }),
  updateDecisionTable: (projectId: string, serviceId: string, nodeId: string, body: { version: number; hitPolicy: HitPolicy; rules: TableRule[] }) => request<DecisionTable>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-table`, { method: 'PUT', body: JSON.stringify(body) }),
  removeDecisionTable: (projectId: string, serviceId: string, nodeId: string, version: number) => request<void>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-table?version=${version}`, { method: 'DELETE' }),
  decisionExpressionSummaries: (projectId: string, serviceId: string) => request<DecisionExpressionSummary[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/decision-expressions`),
  decisionExpression: (projectId: string, serviceId: string, nodeId: string) => request<DecisionExpression | null>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-expression`).then((value) => value ?? null),
  initializeDecisionExpression: (projectId: string, serviceId: string, nodeId: string) => request<DecisionExpression>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-expression`, { method: 'POST' }),
  updateDecisionExpression: (projectId: string, serviceId: string, nodeId: string, body: { version: number; source: string }) => request<DecisionExpression>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-expression`, { method: 'PUT', body: JSON.stringify(body) }),
  removeDecisionExpression: (projectId: string, serviceId: string, nodeId: string, version: number) => request<void>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/graph/nodes/${nodeId}/decision-expression?version=${version}`, { method: 'DELETE' }),
  evaluateDecisionService: (projectId: string, serviceId: string, inputs: Record<string, unknown>) => request<SandboxEvaluation>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/sandbox/evaluations`, { method: 'POST', body: JSON.stringify({ inputs }) }),
  decisionTests: (projectId: string, serviceId: string) => request<DecisionTestCase[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests`),
  createDecisionTest: (projectId: string, serviceId: string, body: unknown) => request<DecisionTestCase>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests`, { method: 'POST', body: JSON.stringify(body) }),
  updateDecisionTest: (projectId: string, serviceId: string, testId: string, body: unknown) => request<DecisionTestCase>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests/${testId}`, { method: 'PUT', body: JSON.stringify(body) }),
  removeDecisionTest: (projectId: string, serviceId: string, testId: string, version: number) => request<void>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests/${testId}?version=${version}`, { method: 'DELETE' }),
  runDecisionTest: (projectId: string, serviceId: string, testId: string) => request<DecisionTestRun>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests/${testId}/runs`, { method: 'POST' }),
  decisionTestRuns: (projectId: string, serviceId: string, testId: string) => request<DecisionTestRun[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/tests/${testId}/runs`),
  regressionMatrices: (projectId: string, serviceId: string) => request<MatrixRunSummary[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/regression-matrices`),
  regressionMatrix: (projectId: string, serviceId: string, runId: string) => request<RegressionMatrix>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/regression-matrices/${runId}`),
  runRegressionMatrix: (projectId: string, serviceId: string, testCaseIds: string[], clientRequestId = crypto.randomUUID()) => request<RegressionMatrix>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/regression-matrices`, { method: 'POST', body: JSON.stringify({ clientRequestId, testCaseIds }) }),
  decisionVersions: (projectId: string, serviceId: string) => request<DecisionVersionSummary[]>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/versions`),
  workingDecisionVersion: (projectId: string, serviceId: string) => request<WorkingDecisionState>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/versions/working`),
  captureDecisionVersion: (projectId: string, serviceId: string, body: { label: string; note: string; expectedServiceVersion: number; expectedGraphVersion: number; expectedFingerprint: string; clientRequestId?: string }) => request<DecisionVersionSummary>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/versions`, { method: 'POST', body: JSON.stringify({ ...body, clientRequestId: body.clientRequestId ?? crypto.randomUUID() }) }),
  decisionVersionDiff: (projectId: string, serviceId: string, baseId: string, targetId: string) => request<DecisionVersionDiff>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/versions/diff?baseId=${baseId}&targetId=${targetId}`),
  approvalWorkflow: (projectId: string, serviceId: string) => request<ApprovalWorkflow>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/change-requests`),
  changeRequestDetail: (projectId: string, serviceId: string, requestId: string) => request<ChangeRequestDetail>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/change-requests/${requestId}`),
  createChangeRequest: (projectId: string, serviceId: string, body: { decisionVersionId: string; title: string; summary: string; clientRequestId?: string }) => request<ChangeRequestView>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/change-requests`, { method: 'POST', body: JSON.stringify({ ...body, clientRequestId: body.clientRequestId ?? crypto.randomUUID() }) }),
  transitionChangeRequest: (projectId: string, serviceId: string, requestId: string, action: 'submit' | 'request-changes' | 'approve' | 'reject' | 'promote', expectedVersion: number, note: string, clientRequestId = crypto.randomUUID()) => request<ChangeRequestView>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/change-requests/${requestId}/${action}`, { method: 'POST', body: JSON.stringify({ clientRequestId, expectedVersion, note }) }),
  runtimeSandbox: (projectId: string, serviceId: string) => request<RuntimeSandbox>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/runtime-sandbox`),
  runtimeDeploymentDetail: (projectId: string, serviceId: string, deploymentId: string) => request<RuntimeDeploymentDetail>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/runtime-sandbox/deployments/${deploymentId}`),
  deployRuntimeVersion: (projectId: string, serviceId: string, decisionVersionId: string, expectedPromotionRevision: number, clientRequestId = crypto.randomUUID()) => request<RuntimeDeployment>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/runtime-sandbox/deployments`, { method: 'POST', body: JSON.stringify({ clientRequestId, decisionVersionId, expectedPromotionRevision }) }),
  transitionRuntimeDeployment: (projectId: string, serviceId: string, deploymentId: string, action: 'retire' | 'rollback', expectedEnvironmentRevision: number, note: string, clientRequestId = crypto.randomUUID()) => request<RuntimeDeployment>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/runtime-sandbox/deployments/${deploymentId}/${action}`, { method: 'POST', body: JSON.stringify({ clientRequestId, expectedEnvironmentRevision, note }) }),
  evaluateRuntimeDeployment: (projectId: string, serviceId: string, inputs: Record<string, unknown>) => request<RuntimeEvaluation>(`/api/v1/projects/${projectId}/decision-services/${serviceId}/runtime-sandbox/evaluations`, { method: 'POST', body: JSON.stringify({ inputs }) }),
  users: () => request<WorkspaceUser[]>('/api/v1/users'),
  createUser: (body: { email: string; displayName: string; tenantRole: string; temporaryPassword: string }) => request<WorkspaceUser>('/api/v1/users', { method: 'POST', body: JSON.stringify(body) }),
  changeUserStatus: (id: string, status: WorkspaceUser['status']) => request<WorkspaceUser>(`/api/v1/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }),
};

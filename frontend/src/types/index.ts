// Mirrors backend/src/main/java/com/zentrox/forge/dto/** - see docs/API.md.

export type TenantStatus = "ACTIVE" | "SUSPENDED";
export type UserStatus = "ACTIVE" | "DISABLED";

export interface Tenant {
  id: string;
  name: string;
  slug: string;
  status: TenantStatus;
  createdAt: string;
}

export interface TenantCreateResponse {
  tenant: Tenant;
  ownerUserId: string;
  ownerEmail: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
  tokenType: "Bearer";
}

export interface UserSummary {
  id: string;
  email: string;
  fullName: string;
  role: string;
  status: UserStatus;
  createdAt: string;
}

export interface WorkflowTransitionRule {
  from: string;
  to: string;
}

export interface WorkflowDefinition {
  id: string;
  name: string;
  description?: string;
  states: string[];
  transitions: WorkflowTransitionRule[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface WorkflowHistoryEntry {
  from: string;
  to: string;
  at: string;
  by: string;
}

export interface WorkflowInstance {
  id: string;
  workflowDefinitionId: string;
  currentState: string;
  history: WorkflowHistoryEntry[];
  createdAt: string;
  updatedAt: string;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  details?: string[];
}

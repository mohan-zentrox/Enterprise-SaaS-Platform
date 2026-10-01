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

/** Pagination envelope returned by list endpoints - mirrors dto/PageResponse.java. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export interface Role {
  id: string;
  name: string;
  systemRole: boolean;
  permissions: string[];
  createdAt: string;
}

export interface AuditLogEntry {
  id: string;
  actorUserId: string | null;
  action: string;
  entityType: string;
  entityId: string | null;
  detailsJson: string | null;
  occurredAt: string;
}

// --- Notifications (FRD-10) ---------------------------------------------------

export interface Notification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  payloadJson: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

// --- Billing (FRD-9) ----------------------------------------------------------

export type SubscriptionPlan = "FREE" | "STARTER" | "PROFESSIONAL" | "ENTERPRISE";
export type SubscriptionStatus = "ACTIVE" | "TRIALING" | "PAST_DUE" | "CANCELED";
export type UsageMetric =
  | "SEATS"
  | "WORKFLOW_DEFINITIONS"
  | "WORKFLOW_INSTANCES_PER_MONTH"
  | "API_KEYS";

/** `limit` is null when the plan grants unlimited use - mirrors dto/UsageResponse.java. */
export interface Usage {
  metric: UsageMetric;
  used: number;
  limit: number | null;
  unlimited: boolean;
}

export interface Subscription {
  plan: SubscriptionPlan;
  status: SubscriptionStatus;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  usage: Usage[];
}

// --- API keys (FRD-12) --------------------------------------------------------

export type ApiScope = "WORKFLOWS_READ" | "INSTANCES_READ";

export interface ApiKey {
  id: string;
  name: string;
  maskedKey: string;
  scopes: ApiScope[];
  createdAt: string;
  lastUsedAt: string | null;
  expiresAt: string | null;
  revokedAt: string | null;
  active: boolean;
}

/** The `secret` here is the only copy that will ever exist. */
export interface ApiKeyCreated {
  key: ApiKey;
  secret: string;
  warning: string;
}

// --- Dashboards (FRD-11) ------------------------------------------------------

export type WidgetType =
  | "WORKFLOW_DEFINITION_COUNT"
  | "WORKFLOW_INSTANCE_COUNT"
  | "INSTANCES_BY_STATE"
  | "ACTIVE_USER_COUNT"
  | "RECENT_AUDIT_ACTIVITY"
  | "SUBSCRIPTION_USAGE";

export interface Widget {
  id: string;
  widgetType: WidgetType;
  title: string | null;
  configJson: string;
  position: number;
  /** Resolved server-side on read; shape depends on widgetType. */
  data: unknown;
}

export interface Dashboard {
  id: string;
  name: string;
  shared: boolean;
  isDefault: boolean;
  layoutJson: string;
  widgets: Widget[];
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

import { apiClient } from "@/api/client";
import type { WorkflowDefinition, WorkflowInstance, WorkflowTransitionRule } from "@/types";

export interface WorkflowDefinitionPayload {
  name: string;
  description?: string;
  states: string[];
  transitions: WorkflowTransitionRule[];
}

export async function listWorkflowDefinitions(): Promise<WorkflowDefinition[]> {
  const { data } = await apiClient.get<WorkflowDefinition[]>("/workflows/definitions");
  return data;
}

export async function createWorkflowDefinition(
  payload: WorkflowDefinitionPayload,
): Promise<WorkflowDefinition> {
  const { data } = await apiClient.post<WorkflowDefinition>("/workflows/definitions", payload);
  return data;
}

export async function deleteWorkflowDefinition(id: string): Promise<void> {
  await apiClient.delete(`/workflows/definitions/${id}`);
}

export async function listWorkflowInstances(): Promise<WorkflowInstance[]> {
  const { data } = await apiClient.get<WorkflowInstance[]>("/workflows/instances");
  return data;
}

export async function createWorkflowInstance(workflowDefinitionId: string): Promise<WorkflowInstance> {
  const { data } = await apiClient.post<WorkflowInstance>("/workflows/instances", { workflowDefinitionId });
  return data;
}

export async function transitionWorkflowInstance(
  id: string,
  toState: string,
): Promise<WorkflowInstance> {
  const { data } = await apiClient.post<WorkflowInstance>(`/workflows/instances/${id}/transitions`, {
    toState,
  });
  return data;
}

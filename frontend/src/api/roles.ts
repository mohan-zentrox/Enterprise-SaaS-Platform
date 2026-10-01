import { apiClient } from "@/api/client";
import type { Role } from "@/types";

export interface RoleWritePayload {
  name: string;
  permissions: string[];
}

export async function listRoles(): Promise<Role[]> {
  const { data } = await apiClient.get<Role[]>("/roles");
  return data;
}

/** The permission catalog, so the role editor doesn't hardcode a copy of the backend enum. */
export async function listPermissions(): Promise<string[]> {
  const { data } = await apiClient.get<string[]>("/permissions");
  return data;
}

export async function createRole(payload: RoleWritePayload): Promise<Role> {
  const { data } = await apiClient.post<Role>("/roles", payload);
  return data;
}

export async function updateRole(id: string, payload: RoleWritePayload): Promise<Role> {
  const { data } = await apiClient.put<Role>(`/roles/${id}`, payload);
  return data;
}

export async function deleteRole(id: string): Promise<void> {
  await apiClient.delete(`/roles/${id}`);
}

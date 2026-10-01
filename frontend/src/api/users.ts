import { apiClient } from "@/api/client";
import type { Page, UserSummary } from "@/types";

export interface UserCreatePayload {
  fullName: string;
  email: string;
  initialPassword: string;
  roleName: string;
}

export interface UserUpdatePayload {
  fullName?: string;
  roleName?: string;
}

export async function listUsers(page = 0, size = 20): Promise<Page<UserSummary>> {
  const { data } = await apiClient.get<Page<UserSummary>>("/users", { params: { page, size } });
  return data;
}

export async function createUser(payload: UserCreatePayload): Promise<UserSummary> {
  const { data } = await apiClient.post<UserSummary>("/users", payload);
  return data;
}

export async function updateUser(id: string, payload: UserUpdatePayload): Promise<UserSummary> {
  const { data } = await apiClient.patch<UserSummary>(`/users/${id}`, payload);
  return data;
}

export async function deactivateUser(id: string): Promise<UserSummary> {
  const { data } = await apiClient.post<UserSummary>(`/users/${id}/deactivate`);
  return data;
}

export async function activateUser(id: string): Promise<UserSummary> {
  const { data } = await apiClient.post<UserSummary>(`/users/${id}/activate`);
  return data;
}

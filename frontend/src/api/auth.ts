import { apiClient } from "@/api/client";
import type { AuthResponse, UserSummary } from "@/types";

export interface LoginPayload {
  tenantSlug: string;
  email: string;
  password: string;
}

export interface RegisterPayload {
  tenantSlug: string;
  fullName: string;
  email: string;
  password: string;
}

export async function login(payload: LoginPayload): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/auth/login", payload);
  return data;
}

export async function register(payload: RegisterPayload): Promise<UserSummary> {
  const { data } = await apiClient.post<UserSummary>("/auth/register", payload);
  return data;
}

export async function logout(refreshToken: string): Promise<void> {
  await apiClient.post("/auth/logout", { refreshToken });
}

import { apiClient } from "@/api/client";
import type { ApiKey, ApiKeyCreated, ApiScope } from "@/types";

export interface ApiKeyCreatePayload {
  name: string;
  scopes: ApiScope[];
  expiresAt?: string | null;
}

export async function listApiKeys(): Promise<ApiKey[]> {
  const { data } = await apiClient.get<ApiKey[]>("/api-keys");
  return data;
}

/** The response carries the plaintext secret - the only time it exists. */
export async function createApiKey(payload: ApiKeyCreatePayload): Promise<ApiKeyCreated> {
  const { data } = await apiClient.post<ApiKeyCreated>("/api-keys", payload);
  return data;
}

export async function revokeApiKey(id: string): Promise<ApiKey> {
  const { data } = await apiClient.delete<ApiKey>(`/api-keys/${id}`);
  return data;
}

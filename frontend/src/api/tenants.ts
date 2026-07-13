import { apiClient } from "@/api/client";
import type { TenantCreateResponse } from "@/types";

export interface TenantCreatePayload {
  name: string;
  slug: string;
  ownerFullName: string;
  ownerEmail: string;
  ownerPassword: string;
}

export async function createTenant(payload: TenantCreatePayload): Promise<TenantCreateResponse> {
  const { data } = await apiClient.post<TenantCreateResponse>("/tenants", payload);
  return data;
}

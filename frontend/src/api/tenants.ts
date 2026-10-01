import { apiClient } from "@/api/client";
import type { Tenant, TenantCreateResponse } from "@/types";

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

export async function getCurrentTenant(): Promise<Tenant> {
  const { data } = await apiClient.get<Tenant>("/tenants/current");
  return data;
}

export async function renameCurrentTenant(name: string): Promise<Tenant> {
  const { data } = await apiClient.patch<Tenant>("/tenants/current", { name });
  return data;
}

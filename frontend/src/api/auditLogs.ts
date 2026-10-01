import { apiClient } from "@/api/client";
import type { AuditLogEntry, Page } from "@/types";

export async function listAuditLogs(page = 0, size = 50): Promise<Page<AuditLogEntry>> {
  const { data } = await apiClient.get<Page<AuditLogEntry>>("/audit-logs", { params: { page, size } });
  return data;
}

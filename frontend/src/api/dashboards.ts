import { apiClient } from "@/api/client";
import type { Dashboard, WidgetType } from "@/types";

export interface WidgetPayload {
  widgetType: WidgetType;
  title?: string;
  configJson?: string;
  position: number;
}

export interface DashboardPayload {
  name: string;
  shared: boolean;
  layoutJson?: string;
  widgets: WidgetPayload[];
}

/** The list view omits widget data; open one to have it resolved. */
export async function listDashboards(): Promise<Dashboard[]> {
  const { data } = await apiClient.get<Dashboard[]>("/dashboards");
  return data;
}

export async function getDashboard(id: string): Promise<Dashboard> {
  const { data } = await apiClient.get<Dashboard>(`/dashboards/${id}`);
  return data;
}

export async function createDashboard(payload: DashboardPayload): Promise<Dashboard> {
  const { data } = await apiClient.post<Dashboard>("/dashboards", payload);
  return data;
}

export async function updateDashboard(id: string, payload: DashboardPayload): Promise<Dashboard> {
  const { data } = await apiClient.put<Dashboard>(`/dashboards/${id}`, payload);
  return data;
}

export async function deleteDashboard(id: string): Promise<void> {
  await apiClient.delete(`/dashboards/${id}`);
}

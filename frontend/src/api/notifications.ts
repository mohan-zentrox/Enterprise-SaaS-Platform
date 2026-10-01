import { apiClient } from "@/api/client";
import type { Notification, Page } from "@/types";

export async function listNotifications(page = 0, size = 20): Promise<Page<Notification>> {
  const { data } = await apiClient.get<Page<Notification>>("/notifications", { params: { page, size } });
  return data;
}

export async function unreadCount(): Promise<number> {
  const { data } = await apiClient.get<{ unread: number }>("/notifications/unread-count");
  return data.unread;
}

export async function markRead(id: string): Promise<Notification> {
  const { data } = await apiClient.post<Notification>(`/notifications/${id}/read`);
  return data;
}

export async function markAllRead(): Promise<number> {
  const { data } = await apiClient.post<{ markedRead: number }>("/notifications/read-all");
  return data.markedRead;
}

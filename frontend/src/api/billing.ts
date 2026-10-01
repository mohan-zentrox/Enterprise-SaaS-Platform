import { apiClient } from "@/api/client";
import type { Subscription, SubscriptionPlan } from "@/types";

export async function getSubscription(): Promise<Subscription> {
  const { data } = await apiClient.get<Subscription>("/billing/subscription");
  return data;
}

export async function changePlan(plan: SubscriptionPlan): Promise<Subscription> {
  const { data } = await apiClient.post<Subscription>("/billing/subscription/plan", { plan });
  return data;
}

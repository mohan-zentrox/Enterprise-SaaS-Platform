import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/store/authStore";
import type { AuthResponse } from "@/types";

export const apiClient = axios.create({
  baseURL: "/v1",
  headers: { "Content-Type": "application/json" },
});

apiClient.interceptors.request.use((config) => {
  const { accessToken } = useAuthStore.getState();
  if (accessToken) {
    config.headers.set("Authorization", `Bearer ${accessToken}`);
  }
  return config;
});

// Coalesce concurrent refreshes into a single in-flight request.
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const { refreshToken, setAccessToken, clear } = useAuthStore.getState();
  if (!refreshToken) {
    clear();
    throw new Error("No refresh token available");
  }

  if (!refreshPromise) {
    refreshPromise = axios
      .post<AuthResponse>("/v1/auth/refresh", { refreshToken })
      .then(({ data }) => {
        setAccessToken(data.accessToken, data.refreshToken);
        return data.accessToken;
      })
      .catch((err) => {
        clear();
        throw err;
      })
      .finally(() => {
        refreshPromise = null;
      });
  }
  return refreshPromise;
}

interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const original = error.config as RetriableConfig | undefined;
    if (error.response?.status === 401 && original && !original._retried) {
      original._retried = true;
      try {
        const newAccessToken = await refreshAccessToken();
        original.headers.set("Authorization", `Bearer ${newAccessToken}`);
        return apiClient(original);
      } catch {
        window.location.assign("/login");
      }
    }
    return Promise.reject(error);
  },
);

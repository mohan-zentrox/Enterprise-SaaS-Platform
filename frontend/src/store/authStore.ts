import { create } from "zustand";
import { persist } from "zustand/middleware";

interface AuthState {
  tenantSlug: string | null;
  accessToken: string | null;
  refreshToken: string | null;
  userEmail: string | null;
  setSession: (session: {
    tenantSlug: string;
    accessToken: string;
    refreshToken: string;
    userEmail: string;
  }) => void;
  setAccessToken: (accessToken: string, refreshToken: string) => void;
  clear: () => void;
}

/**
 * Zustand store for the authenticated session. Persisted to localStorage so a page
 * refresh doesn't force a re-login - the access token is short-lived anyway (see
 * backend forge.jwt.access-token-ttl-minutes), and api/client.ts transparently
 * refreshes it using the refresh token on a 401.
 */
export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      tenantSlug: null,
      accessToken: null,
      refreshToken: null,
      userEmail: null,
      setSession: ({ tenantSlug, accessToken, refreshToken, userEmail }) =>
        set({ tenantSlug, accessToken, refreshToken, userEmail }),
      setAccessToken: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
      clear: () => set({ tenantSlug: null, accessToken: null, refreshToken: null, userEmail: null }),
    }),
    { name: "forge-auth" },
  ),
);

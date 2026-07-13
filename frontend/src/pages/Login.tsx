import { FormEvent, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { login } from "@/api/auth";
import { useAuthStore } from "@/store/authStore";

interface LocationState {
  tenantSlug?: string;
}

export function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const setSession = useAuthStore((state) => state.setSession);

  const locationState = (location.state as LocationState) ?? {};
  const [form, setForm] = useState({
    tenantSlug: locationState.tenantSlug ?? "",
    email: "",
    password: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function update<K extends keyof typeof form>(key: K, value: string) {
    setForm((prev) => ({ ...prev, [key]: value }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const response = await login(form);
      setSession({
        tenantSlug: form.tenantSlug,
        accessToken: response.accessToken,
        refreshToken: response.refreshToken,
        userEmail: form.email,
      });
      navigate("/workflows");
    } catch {
      setError("Invalid tenant, email, or password");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <form onSubmit={handleSubmit} className="w-full max-w-sm space-y-4 rounded-xl bg-white p-8 shadow-sm">
        <h1 className="text-xl font-semibold text-slate-900">Log in</h1>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Organization slug</span>
          <input
            required
            className="input"
            value={form.tenantSlug}
            onChange={(e) => update("tenantSlug", e.target.value)}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Email</span>
          <input
            required
            type="email"
            className="input"
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
          />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Password</span>
          <input
            required
            type="password"
            className="input"
            value={form.password}
            onChange={(e) => update("password", e.target.value)}
          />
        </label>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-forge-600 px-4 py-2 text-sm font-semibold text-white hover:bg-forge-700 disabled:opacity-50"
        >
          {submitting ? "Logging in..." : "Log in"}
        </button>

        <p className="text-center text-sm text-slate-500">
          Need an organization? <Link to="/signup" className="text-forge-600">Create one</Link>
        </p>
      </form>
    </div>
  );
}

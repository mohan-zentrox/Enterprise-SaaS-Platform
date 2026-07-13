import { FormEvent, ReactNode, useState } from "react";
import { useNavigate, Link } from "react-router-dom";
import { createTenant } from "@/api/tenants";

export function Signup() {
  const navigate = useNavigate();
  const [form, setForm] = useState({
    name: "",
    slug: "",
    ownerFullName: "",
    ownerEmail: "",
    ownerPassword: "",
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
      await createTenant(form);
      navigate("/login", { state: { tenantSlug: form.slug } });
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to create tenant");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-50">
      <form onSubmit={handleSubmit} className="w-full max-w-md space-y-4 rounded-xl bg-white p-8 shadow-sm">
        <div>
          <h1 className="text-xl font-semibold text-slate-900">Create your organization</h1>
          <p className="mt-1 text-sm text-slate-500">
            This creates your tenant and its first Owner account in one step.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <Field label="Organization name">
          <input
            required
            className="input"
            value={form.name}
            onChange={(e) => update("name", e.target.value)}
          />
        </Field>
        <Field label="URL slug (lowercase, hyphenated)">
          <input
            required
            pattern="^[a-z0-9]+(-[a-z0-9]+)*$"
            className="input"
            value={form.slug}
            onChange={(e) => update("slug", e.target.value)}
          />
        </Field>
        <Field label="Your full name">
          <input
            required
            className="input"
            value={form.ownerFullName}
            onChange={(e) => update("ownerFullName", e.target.value)}
          />
        </Field>
        <Field label="Your email">
          <input
            required
            type="email"
            className="input"
            value={form.ownerEmail}
            onChange={(e) => update("ownerEmail", e.target.value)}
          />
        </Field>
        <Field label="Password">
          <input
            required
            minLength={8}
            type="password"
            className="input"
            value={form.ownerPassword}
            onChange={(e) => update("ownerPassword", e.target.value)}
          />
        </Field>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-forge-600 px-4 py-2 text-sm font-semibold text-white hover:bg-forge-700 disabled:opacity-50"
        >
          {submitting ? "Creating..." : "Create organization"}
        </button>

        <p className="text-center text-sm text-slate-500">
          Already have an account? <Link to="/login" className="text-forge-600">Log in</Link>
        </p>
      </form>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="block">
      <span className="mb-1 block text-sm font-medium text-slate-700">{label}</span>
      {children}
    </label>
  );
}

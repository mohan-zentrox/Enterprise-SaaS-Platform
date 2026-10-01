import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { NavBar } from "@/components/NavBar";
import { useAuthStore } from "@/store/authStore";
import { getCurrentTenant, renameCurrentTenant } from "@/api/tenants";
import { describeApiError } from "@/api/errors";
import type { Tenant } from "@/types";

/**
 * Tenant admin landing page: organization identity plus entry points to every administration
 * surface. Nothing here is hidden by permission - the server is the authority, and each destination
 * surfaces its own permission error rather than silently disappearing from the menu.
 */
export function AdminConsole() {
  const { userEmail } = useAuthStore();
  const [tenant, setTenant] = useState<Tenant | null>(null);
  const [name, setName] = useState("");
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    void (async () => {
      try {
        const current = await getCurrentTenant();
        setTenant(current);
        setName(current.name);
      } catch (err) {
        // TENANT_READ is granted to every system role, so this failing usually means a custom role.
        setError(describeApiError(err, "Could not load your organization details."));
      }
    })();
  }, []);

  async function handleRename() {
    setError(null);
    setNotice(null);
    try {
      const updated = await renameCurrentTenant(name);
      setTenant(updated);
      setEditing(false);
      setNotice("Organization name updated.");
    } catch (err) {
      setError(describeApiError(err, "Could not rename the organization."));
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">Admin console</h1>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {notice && <p className="rounded-md bg-forge-50 px-3 py-2 text-sm text-forge-700">{notice}</p>}

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Organization
          </h2>
          <dl className="grid gap-4 sm:grid-cols-3">
            <div className="sm:col-span-2">
              <dt className="text-sm text-slate-500">Name</dt>
              <dd className="mt-1">
                {editing ? (
                  <div className="flex flex-wrap gap-2">
                    <input
                      className="input max-w-xs"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                    />
                    <button
                      onClick={() => void handleRename()}
                      className="rounded-md bg-forge-600 px-3 py-2 text-sm font-medium text-white hover:bg-forge-700"
                    >
                      Save
                    </button>
                    <button
                      onClick={() => {
                        setEditing(false);
                        setName(tenant?.name ?? "");
                      }}
                      className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                    >
                      Cancel
                    </button>
                  </div>
                ) : (
                  <div className="flex items-center gap-3">
                    <span className="font-medium text-slate-900">{tenant?.name ?? "—"}</span>
                    <button
                      onClick={() => setEditing(true)}
                      className="text-xs font-medium text-forge-600 hover:text-forge-700"
                    >
                      Rename
                    </button>
                  </div>
                )}
              </dd>
            </div>
            <div>
              <dt className="text-sm text-slate-500">URL slug</dt>
              <dd className="mt-1 font-mono text-sm text-slate-900">{tenant?.slug ?? "—"}</dd>
              <p className="mt-1 text-xs text-slate-400">
                Fixed — it is part of everyone's sign-in.
              </p>
            </div>
            <div>
              <dt className="text-sm text-slate-500">Status</dt>
              <dd className="mt-1">
                <span
                  className={
                    tenant?.status === "SUSPENDED"
                      ? "rounded bg-red-50 px-2 py-1 text-xs font-medium text-red-700"
                      : "rounded bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700"
                  }
                >
                  {tenant?.status === "SUSPENDED" ? "Suspended" : "Active"}
                </span>
              </dd>
            </div>
            <div className="sm:col-span-2">
              <dt className="text-sm text-slate-500">Signed in as</dt>
              <dd className="mt-1 font-medium text-slate-900">{userEmail ?? "—"}</dd>
            </div>
          </dl>
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">Manage</h2>
          <div className="grid gap-3 sm:grid-cols-3">
            <Link
              to="/users"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">People</span>
              <span className="mt-1 block text-xs text-slate-500">
                Add, deactivate and re-role members
              </span>
            </Link>
            <Link
              to="/roles"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">Roles</span>
              <span className="mt-1 block text-xs text-slate-500">
                System roles and custom permission sets
              </span>
            </Link>
            <Link
              to="/audit-log"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">Audit log</span>
              <span className="mt-1 block text-xs text-slate-500">
                Append-only record of every change
              </span>
            </Link>
            <Link
              to="/billing"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">Plan &amp; usage</span>
              <span className="mt-1 block text-xs text-slate-500">
                Limits, current consumption, plan changes
              </span>
            </Link>
            <Link
              to="/api-keys"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">API keys</span>
              <span className="mt-1 block text-xs text-slate-500">
                Scoped keys for system integrations
              </span>
            </Link>
            <Link
              to="/dashboards"
              className="rounded-lg border border-slate-200 p-4 hover:border-forge-500 hover:bg-forge-50"
            >
              <span className="block text-sm font-medium text-slate-900">Dashboards</span>
              <span className="mt-1 block text-xs text-slate-500">
                Saved views, private or shared
              </span>
            </Link>
          </div>
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Single sign-on
          </h2>
          <p className="text-sm text-slate-600">
            Per-tenant OIDC is available via the API (<code className="text-xs">PUT /v1/tenants/current/sso</code>,
            permission <code className="text-xs">TENANT_UPDATE</code>). Configure your provider's
            issuer, client id and secret, then point it at the callback URL the API returns. SAML
            configuration can be stored but not yet activated.
          </p>
          <p className="mt-2 text-xs text-slate-400">
            No UI for this yet — it is the one administration surface still API-only.
          </p>
        </section>
      </main>
    </div>
  );
}

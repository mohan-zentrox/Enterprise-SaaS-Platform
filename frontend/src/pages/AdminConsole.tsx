import { NavBar } from "@/components/NavBar";
import { useAuthStore } from "@/store/authStore";

/**
 * Tenant admin landing page. Deliberately light: subscription/billing, notifications,
 * and configurable-dashboard widgets are backend scaffolds only (see
 * backend/.../billing, notification, dashboard packages and docs/ARCHITECTURE.md), so
 * this page surfaces what is actually implemented today - session/tenant identity and
 * links into the working modules - rather than presenting cards for features that
 * would 501 if clicked.
 */
export function AdminConsole() {
  const { tenantSlug, userEmail } = useAuthStore();

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">Admin Console</h1>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Session
          </h2>
          <dl className="grid grid-cols-2 gap-4 text-sm">
            <div>
              <dt className="text-slate-500">Organization</dt>
              <dd className="font-medium text-slate-900">{tenantSlug ?? "-"}</dd>
            </div>
            <div>
              <dt className="text-slate-500">Signed in as</dt>
              <dd className="font-medium text-slate-900">{userEmail ?? "-"}</dd>
            </div>
          </dl>
        </section>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Not yet implemented (backend scaffold only)
          </h2>
          <ul className="list-inside list-disc space-y-1 text-sm text-slate-600">
            <li>Subscription &amp; billing, entitlement enforcement</li>
            <li>In-app and email notifications</li>
            <li>Configurable dashboard widgets</li>
            <li>Public API-key-authenticated endpoints</li>
            <li>SAML/OIDC single sign-on</li>
          </ul>
        </section>
      </main>
    </div>
  );
}

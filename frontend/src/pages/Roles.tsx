import { NavBar } from "@/components/NavBar";

// Mirrors backend/src/main/java/com/zentrox/forge/service/RoleCatalog.java. There is
// no GET /v1/roles endpoint yet (role management beyond the three seeded system roles
// is out of scope for this foundation - see docs/ARCHITECTURE.md), so this page
// documents the real, enforced grants rather than faking a fetched list.
const SYSTEM_ROLES: { name: string; description: string; permissions: string[] }[] = [
  {
    name: "OWNER",
    description: "Full control, including suspending the tenant itself.",
    permissions: ["All permissions"],
  },
  {
    name: "ADMIN",
    description: "Full operational control except suspending the tenant.",
    permissions: ["All permissions except TENANT_SUSPEND"],
  },
  {
    name: "MEMBER",
    description: "Day-to-day workflow usage.",
    permissions: [
      "TENANT_READ",
      "USER_READ",
      "WORKFLOW_DEFINITION_READ",
      "WORKFLOW_INSTANCE_READ",
      "WORKFLOW_INSTANCE_CREATE",
      "WORKFLOW_INSTANCE_TRANSITION",
    ],
  },
];

export function Roles() {
  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <h1 className="text-2xl font-semibold text-slate-900">Roles &amp; Permissions</h1>
        <p className="text-sm text-slate-500">
          Every new organization is seeded with these three system roles. Permission checks are
          enforced server-side on every write endpoint (Spring Security <code>@PreAuthorize</code>) -
          this page is a read-only reference.
        </p>

        <div className="grid gap-4 sm:grid-cols-3">
          {SYSTEM_ROLES.map((role) => (
            <div key={role.name} className="rounded-xl bg-white p-5 shadow-sm">
              <h2 className="text-sm font-semibold text-forge-700">{role.name}</h2>
              <p className="mt-1 text-sm text-slate-600">{role.description}</p>
              <ul className="mt-3 space-y-1 text-xs text-slate-500">
                {role.permissions.map((permission) => (
                  <li key={permission} className="rounded bg-slate-100 px-2 py-1">
                    {permission}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      </main>
    </div>
  );
}

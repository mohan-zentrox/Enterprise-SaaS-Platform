import { FormEvent, useCallback, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import {
  activateUser,
  createUser,
  deactivateUser,
  listUsers,
  updateUser,
} from "@/api/users";
import { listRoles } from "@/api/roles";
import { describeApiError } from "@/api/errors";
import type { Role, UserSummary } from "@/types";

const PAGE_SIZE = 20;

export function Users() {
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [form, setForm] = useState({
    fullName: "",
    email: "",
    initialPassword: "",
    roleName: "",
  });

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [userPage, roleList] = await Promise.all([listUsers(page, PAGE_SIZE), listRoles()]);
      setUsers(userPage.content);
      setHasNext(userPage.hasNext);
      setTotalElements(userPage.totalElements);
      setRoles(roleList);
      // Default the invite form's role to MEMBER when it exists, else the first role.
      setForm((prev) =>
        prev.roleName
          ? prev
          : { ...prev, roleName: roleList.find((r) => r.name === "MEMBER")?.name ?? roleList[0]?.name ?? "" },
      );
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load users. This page needs the USER_READ permission."));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function handleInvite(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    try {
      const created = await createUser(form);
      setNotice(
        `${created.email} added as ${created.role}. Share the initial password with them directly — ` +
          `there is no invitation email yet, and they should change it on first login.`,
      );
      setForm({ fullName: "", email: "", initialPassword: "", roleName: form.roleName });
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not add this user."));
    }
  }

  async function handleToggleStatus(user: UserSummary) {
    setError(null);
    setNotice(null);
    try {
      if (user.status === "ACTIVE") {
        await deactivateUser(user.id);
        setNotice(
          `${user.email} deactivated. Their existing access token stays valid until it expires ` +
            `(15 minutes by default); they cannot refresh it.`,
        );
      } else {
        await activateUser(user.id);
        setNotice(`${user.email} reactivated.`);
      }
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not change this user's status."));
    }
  }

  async function handleRoleChange(user: UserSummary, roleName: string) {
    if (roleName === user.role) return;
    setError(null);
    setNotice(null);
    try {
      await updateUser(user.id, { roleName });
      setNotice(`${user.email} is now ${roleName}.`);
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not change this user's role."));
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">People</h1>
          <p className="mt-1 text-sm text-slate-500">
            Everyone in your organization. Adding someone here is the only way to create an account —
            public self-registration is disabled.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {notice && <p className="rounded-md bg-forge-50 px-3 py-2 text-sm text-forge-700">{notice}</p>}

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
            Add someone
          </h2>
          <form onSubmit={handleInvite} className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">Full name</span>
              <input
                required
                className="input"
                value={form.fullName}
                onChange={(e) => setForm({ ...form, fullName: e.target.value })}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">Email</span>
              <input
                required
                type="email"
                className="input"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">Initial password</span>
              <input
                required
                minLength={8}
                type="password"
                className="input"
                value={form.initialPassword}
                onChange={(e) => setForm({ ...form, initialPassword: e.target.value })}
              />
              <span className="mt-1 block text-xs text-slate-500">
                At least 8 characters. You will need to share this with them yourself.
              </span>
            </label>
            <label className="block text-sm">
              <span className="mb-1 block font-medium text-slate-700">Role</span>
              <select
                required
                className="input"
                value={form.roleName}
                onChange={(e) => setForm({ ...form, roleName: e.target.value })}
              >
                {roles.map((role) => (
                  <option key={role.id} value={role.name}>
                    {role.name}
                  </option>
                ))}
              </select>
            </label>
            <div className="sm:col-span-2">
              <button
                type="submit"
                className="rounded-md bg-forge-600 px-4 py-2 text-sm font-medium text-white hover:bg-forge-700"
              >
                Add person
              </button>
            </div>
          </form>
        </section>

        <section className="overflow-hidden rounded-xl bg-white shadow-sm">
          <div className="flex items-baseline justify-between border-b border-slate-200 px-6 py-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              {totalElements} {totalElements === 1 ? "person" : "people"}
            </h2>
            {loading && <span className="text-xs text-slate-400">Loading…</span>}
          </div>

          {!loading && users.length === 0 ? (
            <p className="px-6 py-8 text-sm text-slate-500">Nobody here yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-6 py-3 font-medium">Name</th>
                    <th className="px-6 py-3 font-medium">Email</th>
                    <th className="px-6 py-3 font-medium">Role</th>
                    <th className="px-6 py-3 font-medium">Status</th>
                    <th className="px-6 py-3 font-medium sr-only">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {users.map((user) => (
                    <tr key={user.id} className="border-b border-slate-100 last:border-b-0">
                      <td className="px-6 py-3 font-medium text-slate-900">{user.fullName}</td>
                      <td className="px-6 py-3 text-slate-600">{user.email}</td>
                      <td className="px-6 py-3">
                        <select
                          className="rounded border border-slate-300 px-2 py-1 text-sm"
                          value={user.role}
                          onChange={(e) => void handleRoleChange(user, e.target.value)}
                        >
                          {roles.map((role) => (
                            <option key={role.id} value={role.name}>
                              {role.name}
                            </option>
                          ))}
                        </select>
                      </td>
                      <td className="px-6 py-3">
                        <span
                          className={
                            user.status === "ACTIVE"
                              ? "rounded bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700"
                              : "rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600"
                          }
                        >
                          {user.status === "ACTIVE" ? "Active" : "Deactivated"}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-right">
                        <button
                          onClick={() => void handleToggleStatus(user)}
                          className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                        >
                          {user.status === "ACTIVE" ? "Deactivate" : "Reactivate"}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {(page > 0 || hasNext) && (
            <div className="flex items-center justify-between border-t border-slate-200 px-6 py-3 text-sm">
              <button
                disabled={page === 0}
                onClick={() => setPage((p) => Math.max(p - 1, 0))}
                className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 disabled:opacity-40"
              >
                Previous
              </button>
              <span className="text-slate-500">Page {page + 1}</span>
              <button
                disabled={!hasNext}
                onClick={() => setPage((p) => p + 1)}
                className="rounded-md border border-slate-300 px-3 py-1 font-medium text-slate-700 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

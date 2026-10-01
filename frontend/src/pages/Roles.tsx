import { FormEvent, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import { createRole, deleteRole, listPermissions, listRoles, updateRole } from "@/api/roles";
import { describeApiError } from "@/api/errors";
import type { Role } from "@/types";

/**
 * Backed by GET /v1/roles and GET /v1/permissions. This page used to be a hardcoded copy of
 * RoleCatalog.java because no role endpoint existed; it now reflects the real, enforced grants and
 * can create custom roles.
 *
 * Two rules are enforced by the server, not here: system roles cannot be edited or deleted, and a
 * caller cannot grant a permission they do not hold themselves. This page hides the edit/delete
 * buttons on system roles, but it does not pre-filter the permission checkboxes - the authority is
 * RoleManagementService, and its rejection message names the exact offending permissions, which is
 * more useful than silently omitting them from the list.
 */
export function Roles() {
  const [roles, setRoles] = useState<Role[]>([]);
  const [permissionCatalog, setPermissionCatalog] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState("");
  const [selected, setSelected] = useState<Set<string>>(new Set());

  async function refresh() {
    setLoading(true);
    try {
      const [roleList, permissions] = await Promise.all([listRoles(), listPermissions()]);
      setRoles(roleList);
      setPermissionCatalog(permissions);
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load roles. This page needs the USER_READ permission."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  function resetForm() {
    setEditingId(null);
    setName("");
    setSelected(new Set());
  }

  function startEditing(role: Role) {
    setEditingId(role.id);
    setName(role.name);
    setSelected(new Set(role.permissions));
    setNotice(null);
    setError(null);
  }

  function togglePermission(permission: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(permission)) {
        next.delete(permission);
      } else {
        next.add(permission);
      }
      return next;
    });
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    const payload = { name, permissions: [...selected] };
    try {
      if (editingId) {
        await updateRole(editingId, payload);
        setNotice(`Role ${name} updated.`);
      } else {
        await createRole(payload);
        setNotice(`Role ${name} created.`);
      }
      resetForm();
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not save this role."));
    }
  }

  async function handleDelete(role: Role) {
    setError(null);
    setNotice(null);
    try {
      await deleteRole(role.id);
      setNotice(`Role ${role.name} deleted.`);
      if (editingId === role.id) resetForm();
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not delete this role."));
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Roles &amp; permissions</h1>
          <p className="mt-1 text-sm text-slate-500">
            Every organization starts with OWNER, ADMIN and MEMBER. Those three cannot be changed or
            deleted; add a custom role for anything else. Permission checks are enforced server-side on
            every request.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}
        {notice && <p className="rounded-md bg-forge-50 px-3 py-2 text-sm text-forge-700">{notice}</p>}

        <div className="grid gap-4 lg:grid-cols-3">
          {loading && roles.length === 0 ? (
            <p className="text-sm text-slate-500">Loading…</p>
          ) : (
            roles.map((role) => (
              <div key={role.id} className="flex flex-col rounded-xl bg-white p-5 shadow-sm">
                <div className="flex items-start justify-between gap-2">
                  <h2 className="text-sm font-semibold text-forge-700">{role.name}</h2>
                  {role.systemRole && (
                    <span className="rounded bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-600">
                      System
                    </span>
                  )}
                </div>
                <p className="mt-1 text-xs text-slate-500">
                  {role.permissions.length} of {permissionCatalog.length} permissions
                </p>
                <ul className="mt-3 flex-1 space-y-1 text-xs text-slate-500">
                  {role.permissions.slice(0, 6).map((permission) => (
                    <li key={permission} className="rounded bg-slate-100 px-2 py-1">
                      {permission}
                    </li>
                  ))}
                  {role.permissions.length > 6 && (
                    <li className="px-2 py-1 text-slate-400">
                      + {role.permissions.length - 6} more
                    </li>
                  )}
                </ul>
                {!role.systemRole && (
                  <div className="mt-4 flex gap-2">
                    <button
                      onClick={() => startEditing(role)}
                      className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => void handleDelete(role)}
                      className="rounded-md border border-red-200 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
                    >
                      Delete
                    </button>
                  </div>
                )}
              </div>
            ))
          )}
        </div>

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
            {editingId ? "Edit role" : "New custom role"}
          </h2>
          <form onSubmit={handleSubmit} className="space-y-4">
            <label className="block max-w-sm text-sm">
              <span className="mb-1 block font-medium text-slate-700">Role name</span>
              <input
                required
                className="input"
                placeholder="AUDITOR"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>

            <fieldset>
              <legend className="mb-2 text-sm font-medium text-slate-700">Permissions</legend>
              <div className="grid gap-x-6 gap-y-2 sm:grid-cols-2 lg:grid-cols-3">
                {permissionCatalog.map((permission) => (
                  <label key={permission} className="flex items-center gap-2 text-xs text-slate-600">
                    <input
                      type="checkbox"
                      className="rounded border-slate-300 text-forge-600 focus:ring-forge-500"
                      checked={selected.has(permission)}
                      onChange={() => togglePermission(permission)}
                    />
                    <span className="font-mono">{permission}</span>
                  </label>
                ))}
              </div>
            </fieldset>

            <div className="flex gap-2">
              <button
                type="submit"
                className="rounded-md bg-forge-600 px-4 py-2 text-sm font-medium text-white hover:bg-forge-700"
              >
                {editingId ? "Save changes" : "Create role"}
              </button>
              {editingId && (
                <button
                  type="button"
                  onClick={resetForm}
                  className="rounded-md border border-slate-300 px-4 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
                >
                  Cancel
                </button>
              )}
            </div>
          </form>
        </section>
      </main>
    </div>
  );
}

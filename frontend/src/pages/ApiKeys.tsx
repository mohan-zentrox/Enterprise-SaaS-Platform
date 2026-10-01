import { FormEvent, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import { createApiKey, listApiKeys, revokeApiKey } from "@/api/apiKeys";
import { describeApiError } from "@/api/errors";
import type { ApiKey, ApiKeyCreated, ApiScope } from "@/types";

const ALL_SCOPES: { scope: ApiScope; label: string }[] = [
  { scope: "WORKFLOWS_READ", label: "Read workflow definitions" },
  { scope: "INSTANCES_READ", label: "Read workflow instances and their state" },
];

/**
 * API key management. The one interaction that needs care is creation: the response carries the only
 * copy of the secret that will ever exist, so it is surfaced in a panel that has to be dismissed
 * deliberately rather than as a toast that could disappear unread.
 */
export function ApiKeys() {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<ApiKeyCreated | null>(null);
  const [copied, setCopied] = useState(false);

  const [name, setName] = useState("");
  const [scopes, setScopes] = useState<Set<ApiScope>>(new Set(["INSTANCES_READ"]));
  const [expiresAt, setExpiresAt] = useState("");

  async function refresh() {
    setLoading(true);
    try {
      setKeys(await listApiKeys());
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load API keys. This page needs the ROLE_MANAGE permission."));
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void refresh();
  }, []);

  function toggleScope(scope: ApiScope) {
    setScopes((prev) => {
      const next = new Set(prev);
      if (next.has(scope)) next.delete(scope);
      else next.add(scope);
      return next;
    });
  }

  async function handleCreate(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setCopied(false);
    try {
      const result = await createApiKey({
        name,
        scopes: [...scopes],
        // datetime-local gives a local wall-clock string; the API expects an instant.
        expiresAt: expiresAt ? new Date(expiresAt).toISOString() : null,
      });
      setCreated(result);
      setName("");
      setExpiresAt("");
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not create the key."));
    }
  }

  async function handleRevoke(key: ApiKey) {
    setError(null);
    try {
      await revokeApiKey(key.id);
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not revoke that key."));
    }
  }

  async function copySecret() {
    if (!created) return;
    try {
      await navigator.clipboard.writeText(created.secret);
      setCopied(true);
    } catch {
      // Clipboard access is blocked in some contexts; the value is selectable on screen regardless.
      setCopied(false);
    }
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-4xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">API keys</h1>
          <p className="mt-1 text-sm text-slate-500">
            For system integrations calling <code className="text-xs">/v1/public/**</code>. Keys are
            scoped and read-only, and cannot be used against the rest of the API.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        {created && (
          <section className="rounded-xl border-2 border-amber-300 bg-amber-50 p-6">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-amber-900">
              Copy this key now
            </h2>
            <p className="mt-1 text-sm text-amber-800">{created.warning}</p>
            <div className="mt-3 flex flex-wrap items-center gap-2">
              <code className="min-w-0 flex-1 break-all rounded bg-white px-3 py-2 font-mono text-sm text-slate-900">
                {created.secret}
              </code>
              <button
                onClick={() => void copySecret()}
                className="rounded-md bg-amber-700 px-3 py-2 text-sm font-medium text-white hover:bg-amber-800"
              >
                {copied ? "Copied" : "Copy"}
              </button>
            </div>
            <button
              onClick={() => setCreated(null)}
              className="mt-4 text-sm font-medium text-amber-900 underline"
            >
              I have saved it — dismiss
            </button>
          </section>
        )}

        <section className="rounded-xl bg-white p-6 shadow-sm">
          <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
            New key
          </h2>
          <form onSubmit={handleCreate} className="space-y-4">
            <label className="block max-w-sm text-sm">
              <span className="mb-1 block font-medium text-slate-700">Name</span>
              <input
                required
                className="input"
                placeholder="CI pipeline"
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>

            <fieldset>
              <legend className="mb-2 text-sm font-medium text-slate-700">Scopes</legend>
              <div className="space-y-2">
                {ALL_SCOPES.map(({ scope, label }) => (
                  <label key={scope} className="flex items-start gap-2 text-sm text-slate-600">
                    <input
                      type="checkbox"
                      className="mt-0.5 rounded border-slate-300 text-forge-600 focus:ring-forge-500"
                      checked={scopes.has(scope)}
                      onChange={() => toggleScope(scope)}
                    />
                    <span>
                      <span className="font-mono text-xs">{scope}</span>
                      <span className="block text-xs text-slate-500">{label}</span>
                    </span>
                  </label>
                ))}
              </div>
            </fieldset>

            <label className="block max-w-sm text-sm">
              <span className="mb-1 block font-medium text-slate-700">Expires (optional)</span>
              <input
                type="datetime-local"
                className="input"
                value={expiresAt}
                onChange={(e) => setExpiresAt(e.target.value)}
              />
              <span className="mt-1 block text-xs text-slate-500">Leave empty for a key that never expires.</span>
            </label>

            <button
              type="submit"
              disabled={scopes.size === 0}
              className="rounded-md bg-forge-600 px-4 py-2 text-sm font-medium text-white hover:bg-forge-700 disabled:opacity-50"
            >
              Create key
            </button>
            {scopes.size === 0 && (
              <p className="text-xs text-slate-500">Grant at least one scope — a key with none can do nothing.</p>
            )}
          </form>
        </section>

        <section className="overflow-hidden rounded-xl bg-white shadow-sm">
          <div className="flex items-baseline justify-between border-b border-slate-200 px-6 py-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              {keys.length} {keys.length === 1 ? "key" : "keys"}
            </h2>
            {loading && <span className="text-xs text-slate-400">Loading…</span>}
          </div>

          {!loading && keys.length === 0 ? (
            <p className="px-6 py-8 text-sm text-slate-500">No API keys yet.</p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-6 py-3 font-medium">Name</th>
                    <th className="px-6 py-3 font-medium">Key</th>
                    <th className="px-6 py-3 font-medium">Scopes</th>
                    <th className="px-6 py-3 font-medium">Last used</th>
                    <th className="px-6 py-3 font-medium">Status</th>
                    <th className="px-6 py-3 font-medium sr-only">Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {keys.map((key) => (
                    <tr key={key.id} className="border-b border-slate-100 last:border-b-0">
                      <td className="px-6 py-3 font-medium text-slate-900">{key.name}</td>
                      <td className="px-6 py-3 font-mono text-xs text-slate-500">{key.maskedKey}</td>
                      <td className="px-6 py-3">
                        <span className="font-mono text-xs text-slate-600">{key.scopes.join(", ")}</span>
                      </td>
                      <td className="px-6 py-3 text-slate-500">
                        {key.lastUsedAt ? new Date(key.lastUsedAt).toLocaleString() : "never"}
                      </td>
                      <td className="px-6 py-3">
                        {key.active ? (
                          <span className="rounded bg-emerald-50 px-2 py-1 text-xs font-medium text-emerald-700">
                            Active
                          </span>
                        ) : key.revokedAt ? (
                          <span className="rounded bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">
                            Revoked
                          </span>
                        ) : (
                          <span className="rounded bg-amber-50 px-2 py-1 text-xs font-medium text-amber-800">
                            Expired
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-3 text-right">
                        {key.active && (
                          <button
                            onClick={() => void handleRevoke(key)}
                            className="rounded-md border border-red-200 px-3 py-1 text-xs font-medium text-red-700 hover:bg-red-50"
                          >
                            Revoke
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </main>
    </div>
  );
}

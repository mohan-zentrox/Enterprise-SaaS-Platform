import { useCallback, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import { listAuditLogs } from "@/api/auditLogs";
import { describeApiError } from "@/api/errors";
import type { AuditLogEntry } from "@/types";

const PAGE_SIZE = 50;

/** Backed by GET /v1/audit-logs (permission AUDIT_LOG_READ). Read-only by design. */
export function AuditLog() {
  const [entries, setEntries] = useState<AuditLogEntry[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listAuditLogs(page, PAGE_SIZE);
      setEntries(result.content);
      setHasNext(result.hasNext);
      setTotalElements(result.totalElements);
      setError(null);
    } catch (err) {
      setError(
        describeApiError(err, "Could not load the audit log. This page needs the AUDIT_LOG_READ permission."),
      );
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-5xl space-y-6 p-6">
        <div>
          <h1 className="text-2xl font-semibold text-slate-900">Audit log</h1>
          <p className="mt-1 text-sm text-slate-500">
            Append-only record of every change in your organization, newest first. Entries cannot be
            edited or deleted through this application.
          </p>
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        <section className="overflow-hidden rounded-xl bg-white shadow-sm">
          <div className="flex items-baseline justify-between border-b border-slate-200 px-6 py-4">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
              {totalElements} {totalElements === 1 ? "entry" : "entries"}
            </h2>
            {loading && <span className="text-xs text-slate-400">Loading…</span>}
          </div>

          {!loading && entries.length === 0 ? (
            <p className="px-6 py-8 text-sm text-slate-500">
              Nothing recorded yet. Audit entries appear here as soon as someone makes a change.
            </p>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                    <th className="px-6 py-3 font-medium">When</th>
                    <th className="px-6 py-3 font-medium">Action</th>
                    <th className="px-6 py-3 font-medium">Entity</th>
                    <th className="px-6 py-3 font-medium">Actor</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => (
                    <tr key={entry.id} className="border-b border-slate-100 last:border-b-0">
                      <td className="whitespace-nowrap px-6 py-3 text-slate-600">
                        {new Date(entry.occurredAt).toLocaleString()}
                      </td>
                      <td className="px-6 py-3">
                        <span className="rounded bg-slate-100 px-2 py-1 font-mono text-xs text-slate-700">
                          {entry.action}
                        </span>
                      </td>
                      <td className="px-6 py-3 text-slate-600">
                        {entry.entityType}
                        {entry.entityId && (
                          <span className="ml-1 font-mono text-xs text-slate-400">
                            {entry.entityId.slice(0, 8)}
                          </span>
                        )}
                      </td>
                      <td className="px-6 py-3 font-mono text-xs text-slate-500">
                        {entry.actorUserId ? entry.actorUserId.slice(0, 8) : "system"}
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

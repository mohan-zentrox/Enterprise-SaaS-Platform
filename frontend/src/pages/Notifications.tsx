import { useCallback, useEffect, useState } from "react";
import { NavBar } from "@/components/NavBar";
import { listNotifications, markAllRead, markRead } from "@/api/notifications";
import { describeApiError } from "@/api/errors";
import type { Notification } from "@/types";

const PAGE_SIZE = 20;

/**
 * The caller's own inbox. There is no permission gate and no way to view anyone else's - the backend
 * takes the recipient from the access token, never a parameter.
 */
export function Notifications() {
  const [items, setItems] = useState<Notification[]>([]);
  const [page, setPage] = useState(0);
  const [hasNext, setHasNext] = useState(false);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const result = await listNotifications(page, PAGE_SIZE);
      setItems(result.content);
      setHasNext(result.hasNext);
      setTotal(result.totalElements);
      setError(null);
    } catch (err) {
      setError(describeApiError(err, "Could not load your notifications."));
    } finally {
      setLoading(false);
    }
  }, [page]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  async function handleMarkRead(item: Notification) {
    if (item.read) return;
    try {
      await markRead(item.id);
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not mark that as read."));
    }
  }

  async function handleMarkAllRead() {
    try {
      await markAllRead();
      await refresh();
    } catch (err) {
      setError(describeApiError(err, "Could not mark everything as read."));
    }
  }

  const unread = items.filter((i) => !i.read).length;

  return (
    <div className="min-h-screen bg-slate-50">
      <NavBar />
      <main className="mx-auto max-w-3xl space-y-6 p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h1 className="text-2xl font-semibold text-slate-900">Notifications</h1>
            <p className="mt-1 text-sm text-slate-500">
              {total} {total === 1 ? "notification" : "notifications"}
              {unread > 0 && ` · ${unread} unread on this page`}
            </p>
          </div>
          {unread > 0 && (
            <button
              onClick={() => void handleMarkAllRead()}
              className="rounded-md border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50"
            >
              Mark all as read
            </button>
          )}
        </div>

        {error && <p className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

        {loading && items.length === 0 ? (
          <p className="text-sm text-slate-500">Loading…</p>
        ) : items.length === 0 ? (
          <div className="rounded-xl bg-white p-8 text-center shadow-sm">
            <p className="text-sm text-slate-500">
              Nothing here yet. You will be notified when someone invites you, or when a workflow you
              moved changes state.
            </p>
          </div>
        ) : (
          <ul className="space-y-2">
            {items.map((item) => (
              <li
                key={item.id}
                className={`rounded-xl p-4 shadow-sm ${
                  item.read ? "bg-white" : "border-l-4 border-forge-500 bg-forge-50"
                }`}
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <h2 className="font-medium text-slate-900">{item.title}</h2>
                      <span className="rounded bg-slate-100 px-2 py-0.5 font-mono text-xs text-slate-600">
                        {item.type}
                      </span>
                    </div>
                    {item.body && (
                      <p className="mt-1 whitespace-pre-line text-sm text-slate-600">{item.body}</p>
                    )}
                    <p className="mt-2 text-xs text-slate-400">
                      {new Date(item.createdAt).toLocaleString()}
                      {item.read && item.readAt && ` · read ${new Date(item.readAt).toLocaleString()}`}
                    </p>
                  </div>
                  {!item.read && (
                    <button
                      onClick={() => void handleMarkRead(item)}
                      className="flex-none rounded-md border border-slate-300 bg-white px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50"
                    >
                      Mark read
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}

        {(page > 0 || hasNext) && (
          <div className="flex items-center justify-between text-sm">
            <button
              disabled={page === 0}
              onClick={() => setPage((p) => Math.max(p - 1, 0))}
              className="rounded-md border border-slate-300 bg-white px-3 py-1 font-medium text-slate-700 disabled:opacity-40"
            >
              Previous
            </button>
            <span className="text-slate-500">Page {page + 1}</span>
            <button
              disabled={!hasNext}
              onClick={() => setPage((p) => p + 1)}
              className="rounded-md border border-slate-300 bg-white px-3 py-1 font-medium text-slate-700 disabled:opacity-40"
            >
              Next
            </button>
          </div>
        )}
      </main>
    </div>
  );
}

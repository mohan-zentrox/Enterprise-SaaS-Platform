import { useEffect, useState } from "react";
import { NavLink } from "react-router-dom";
import { unreadCount } from "@/api/notifications";

const POLL_INTERVAL_MS = 60_000;

/**
 * Unread badge in the nav bar.
 *
 * <p>Polls rather than streaming. A WebSocket or SSE channel would be the right answer for instant
 * delivery, but it needs its own auth handshake, reconnection handling and per-instance fan-out;
 * polling a dedicated count endpoint once a minute costs one indexed query against a partial index
 * (see V3's {@code idx_notifications_unread}) and is honest about what it is.
 *
 * <p>Failures are swallowed deliberately - a nav badge must not surface an error banner over
 * whatever page the user is actually on. Note this only suppresses the badge's own error handling:
 * a genuinely expired session still redirects to login, because that happens inside the axios
 * interceptor in api/client.ts, before this catch ever runs. That is the right outcome, just not
 * something this component controls.
 */
export function NotificationBell() {
  const [unread, setUnread] = useState(0);

  useEffect(() => {
    let cancelled = false;

    async function poll() {
      try {
        const count = await unreadCount();
        if (!cancelled) setUnread(count);
      } catch {
        // Ignored on purpose - see the class note above.
      }
    }

    void poll();
    const timer = window.setInterval(() => void poll(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  return (
    <NavLink
      to="/notifications"
      className={({ isActive }) =>
        `relative rounded-md px-3 py-2 text-sm font-medium ${
          isActive ? "bg-forge-600 text-white" : "text-slate-600 hover:bg-forge-50"
        }`
      }
      aria-label={unread > 0 ? `Notifications, ${unread} unread` : "Notifications"}
    >
      Inbox
      {unread > 0 && (
        <span className="absolute -right-1 -top-1 flex h-5 min-w-5 items-center justify-center rounded-full bg-red-500 px-1 text-xs font-semibold text-white">
          {unread > 99 ? "99+" : unread}
        </span>
      )}
    </NavLink>
  );
}

import { NavLink, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import { logout as logoutRequest } from "@/api/auth";
import { NotificationBell } from "@/components/NotificationBell";

const linkClasses = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded-md text-sm font-medium ${
    isActive ? "bg-forge-600 text-white" : "text-slate-600 hover:bg-forge-50"
  }`;

/**
 * Primary navigation. Every link is shown to every authenticated user rather than hidden by
 * permission: the server is the authority, and hiding a link does not secure anything. Pages the
 * caller lacks permission for explain that instead of failing blankly - see each page's error
 * handling, which surfaces the server's own message.
 */
export function NavBar() {
  const navigate = useNavigate();
  const { userEmail, refreshToken, clear } = useAuthStore();

  async function handleLogout() {
    try {
      if (refreshToken) {
        await logoutRequest(refreshToken);
      }
    } finally {
      clear();
      navigate("/login");
    }
  }

  return (
    <nav className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-200 bg-white px-6 py-3">
      <div className="flex flex-wrap items-center gap-1">
        <span className="mr-4 text-lg font-semibold text-forge-700">Project Forge</span>
        <NavLink to="/dashboards" className={linkClasses}>
          Dashboards
        </NavLink>
        <NavLink to="/workflows" className={linkClasses}>
          Workflows
        </NavLink>
        <NotificationBell />
        <NavLink to="/users" className={linkClasses}>
          People
        </NavLink>
        <NavLink to="/roles" className={linkClasses}>
          Roles
        </NavLink>
        <NavLink to="/billing" className={linkClasses}>
          Plan
        </NavLink>
        <NavLink to="/api-keys" className={linkClasses}>
          API keys
        </NavLink>
        <NavLink to="/audit-log" className={linkClasses}>
          Audit log
        </NavLink>
        <NavLink to="/admin" className={linkClasses}>
          Settings
        </NavLink>
      </div>
      <div className="flex items-center gap-4">
        {userEmail && <span className="text-sm text-slate-500">{userEmail}</span>}
        <button
          onClick={handleLogout}
          className="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
        >
          Log out
        </button>
      </div>
    </nav>
  );
}

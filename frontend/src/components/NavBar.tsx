import { NavLink, useNavigate } from "react-router-dom";
import { useAuthStore } from "@/store/authStore";
import { logout as logoutRequest } from "@/api/auth";

const linkClasses = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded-md text-sm font-medium ${
    isActive ? "bg-forge-600 text-white" : "text-slate-600 hover:bg-forge-50"
  }`;

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
    <nav className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-3">
      <div className="flex items-center gap-6">
        <span className="text-lg font-semibold text-forge-700">Project Forge</span>
        <NavLink to="/admin" className={linkClasses}>
          Admin Console
        </NavLink>
        <NavLink to="/roles" className={linkClasses}>
          Roles
        </NavLink>
        <NavLink to="/workflows" className={linkClasses}>
          Workflows
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

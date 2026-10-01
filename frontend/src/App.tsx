import { Navigate, Route, Routes } from "react-router-dom";
import { Signup } from "@/pages/Signup";
import { Login } from "@/pages/Login";
import { AdminConsole } from "@/pages/AdminConsole";
import { Roles } from "@/pages/Roles";
import { Users } from "@/pages/Users";
import { AuditLog } from "@/pages/AuditLog";
import { Workflows } from "@/pages/Workflows";
import { Notifications } from "@/pages/Notifications";
import { Billing } from "@/pages/Billing";
import { ApiKeys } from "@/pages/ApiKeys";
import { Dashboards } from "@/pages/Dashboards";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/signup" element={<Signup />} />
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/dashboards" element={<Dashboards />} />
        <Route path="/workflows" element={<Workflows />} />
        <Route path="/notifications" element={<Notifications />} />
        <Route path="/admin" element={<AdminConsole />} />
        <Route path="/users" element={<Users />} />
        <Route path="/roles" element={<Roles />} />
        <Route path="/billing" element={<Billing />} />
        <Route path="/api-keys" element={<ApiKeys />} />
        <Route path="/audit-log" element={<AuditLog />} />
      </Route>

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

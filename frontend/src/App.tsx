import { Navigate, Route, Routes } from "react-router-dom";
import { Signup } from "@/pages/Signup";
import { Login } from "@/pages/Login";
import { AdminConsole } from "@/pages/AdminConsole";
import { Roles } from "@/pages/Roles";
import { Workflows } from "@/pages/Workflows";
import { ProtectedRoute } from "@/components/ProtectedRoute";

export function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/signup" element={<Signup />} />
      <Route path="/login" element={<Login />} />

      <Route element={<ProtectedRoute />}>
        <Route path="/admin" element={<AdminConsole />} />
        <Route path="/roles" element={<Roles />} />
        <Route path="/workflows" element={<Workflows />} />
      </Route>

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}

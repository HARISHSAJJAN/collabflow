import { Navigate, Route, BrowserRouter, Routes } from "react-router-dom";
import { Toaster } from "react-hot-toast";
import { AppShell } from "./components/layout/AppShell";
import { LoginPage } from "./pages/LoginPage";
import { RegisterPage } from "./pages/RegisterPage";
import { DashboardPage } from "./pages/DashboardPage";
import { TeamDetailPage } from "./pages/TeamDetailPage";
import { ProjectBoardPage } from "./pages/ProjectBoardPage";
import { useAuthStore } from "./store/authStore";
import { useLiveNotifications } from "./hooks/useNotifications";

function ProtectedLayout() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  useLiveNotifications();
  if (!isAuthenticated) return <Navigate to="/login" replace />;
  return <AppShell />;
}

function PublicOnlyRoute({ children }: { children: React.ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  if (isAuthenticated) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <BrowserRouter>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 4000,
          style: { background: "var(--bg-surface)", color: "var(--text-primary)", border: "1px solid var(--border-subtle)" },
        }}
      />
      <Routes>
        <Route
          path="/login"
          element={
            <PublicOnlyRoute>
              <LoginPage />
            </PublicOnlyRoute>
          }
        />
        <Route
          path="/register"
          element={
            <PublicOnlyRoute>
              <RegisterPage />
            </PublicOnlyRoute>
          }
        />
        <Route element={<ProtectedLayout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/teams/:teamId" element={<TeamDetailPage />} />
          <Route path="/projects/:projectId" element={<ProjectBoardPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

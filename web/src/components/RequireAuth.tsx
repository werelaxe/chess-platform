import { Navigate, Outlet, useLocation } from "react-router";
import { useAuthStore } from "../store/auth";

/** Layout route that sends anonymous visitors to the login page and back after signing in. */
export function RequireAuth() {
  const token = useAuthStore((state) => state.token);
  const location = useLocation();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  }
  return <Outlet />;
}

import { isAdmin, isLoggedIn } from "./auth-storage";

// Client-side redirect only, for UX. The server enforces this for real:
// RoleHandler.requireRole(ADMIN) rejects non-admin JWTs with 403 on every
// /api/users/* call, independent of what this page shows.
if (!isLoggedIn()) {
  window.location.replace("/pages/login.html");
} else if (!isAdmin()) {
  window.location.replace("/pages/dashboard.html");
}

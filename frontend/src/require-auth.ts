import { isLoggedIn } from "./auth-storage";

// Client-side redirect only, for UX (don't show a page you can't use).
// The real protection is server-side: every /api/* call still needs a
// valid JWT, and every /api/users/* call still needs the ADMIN role.
if (!isLoggedIn()) {
  window.location.replace("/pages/login.html");
}
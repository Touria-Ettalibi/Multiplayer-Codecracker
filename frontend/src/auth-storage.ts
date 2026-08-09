const TOKEN_KEY = "jwt";

export type TokenClaims = {
  sub: string;
  uid: number;
  role: string;
  exp: number;
};

/** Stores the JWT returned by the login endpoint. */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token);
}

/** Returns the stored JWT, or null if the user is not logged in. */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

/** Removes the stored JWT (logout). */
export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * Decodes the claims of the stored JWT without verifying its signature.
 *
 * This is for UI decisions only (e.g. hiding the admin menu item) — the
 * backend re-validates the signature and expiry on every request, so a
 * tampered token here gains no real access.
 */
export function getClaims(): TokenClaims | null {
  const token = getToken();
  if (!token) return null;

  try {
    const payload = token.split(".")[1];
    const decoded = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decoded) as TokenClaims;
  } catch {
    return null;
  }
}

/** Returns true if a token is stored and not yet expired. */
export function isLoggedIn(): boolean {
  const claims = getClaims();
  if (!claims) return false;
  return claims.exp * 1000 > Date.now();
}

/** Returns true if the logged-in user has the ADMIN role. */
export function isAdmin(): boolean {
  return getClaims()?.role === "ADMIN";
}

/** Builds fetch headers carrying the stored JWT, for authenticated requests. */
export function authHeaders(): HeadersInit {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}
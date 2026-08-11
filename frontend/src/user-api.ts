import { authHeaders } from "./auth-storage";

export type ManagedUser = {
  id: number;
  username: string;
  role: string;
  createdAt: number;
};

async function readError(response: Response, fallback: string): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "error" in body) {
      return String((body as { error: unknown }).error);
    }
  } catch {
    // response had no JSON body — fall back below
  }
  return fallback;
}

/** Loads all users, optionally filtered by a username search term. */
export async function fetchUsers(search?: string): Promise<ManagedUser[]> {
  const query = search ? `?search=${encodeURIComponent(search)}` : "";
  const response = await fetch(`/api/users${query}`, { headers: authHeaders() });

  if (!response.ok) {
    throw new Error(await readError(response, "Benutzer konnten nicht geladen werden."));
  }

  return (await response.json()) as ManagedUser[];
}

/** Creates a new user account with the given role. */
export async function createUser(
  username: string,
  password: string,
  role: string,
): Promise<ManagedUser> {
  const response = await fetch("/api/users", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ username, password, role }),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Benutzer konnte nicht erstellt werden."));
  }

  return (await response.json()) as ManagedUser;
}

/** Updates a user's username and role, and optionally its password. */
export async function updateUser(
  id: number,
  username: string,
  role: string,
  password?: string,
): Promise<ManagedUser> {
  const response = await fetch(`/api/users/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ username, role, password: password || undefined }),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Benutzer konnte nicht aktualisiert werden."));
  }

  return (await response.json()) as ManagedUser;
}

/** Deletes a user by its ID. */
export async function deleteUser(id: number): Promise<void> {
  const response = await fetch(`/api/users/${id}`, {
    method: "DELETE",
    headers: authHeaders(),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Benutzer konnte nicht gelöscht werden."));
  }
}

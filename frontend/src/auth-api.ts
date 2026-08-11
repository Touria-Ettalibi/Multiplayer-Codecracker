export type PublicUser = {
  id: number;
  username: string;
  role: string;
  createdAt: number;
};

// The backend returns error messages in English; the rest of the UI is
// German, so known messages are translated here for a consistent frontend.
const ERROR_TRANSLATIONS: Record<string, string> = {
  "Username is already taken": "Dieser Benutzername ist bereits vergeben.",
  "Username must not be empty": "Der Benutzername darf nicht leer sein.",
  "Username must not exceed 50 characters": "Der Benutzername darf höchstens 50 Zeichen lang sein.",
  "Password must be at least 8 characters long": "Das Passwort muss mindestens 8 Zeichen lang sein.",
  "Invalid username or password": "Benutzername oder Passwort ist falsch.",
};

async function readError(response: Response, fallback: string): Promise<string> {
  try {
    const body: unknown = await response.json();
    if (typeof body === "object" && body !== null && "error" in body) {
      const message = String((body as { error: unknown }).error);
      return ERROR_TRANSLATIONS[message] ?? message;
    }
  } catch {
    // response had no JSON body — fall back below
  }
  return fallback;
}

/** Logs in and returns the signed JWT on success. */
export async function login(username: string, password: string): Promise<string> {
  const response = await fetch("/api/auth/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Anmeldung fehlgeschlagen."));
  }

  const result: unknown = await response.json();
  return (result as { token: string }).token;
}

/** Registers a new account with the default USER role. */
export async function register(username: string, password: string): Promise<PublicUser> {
  const response = await fetch("/api/auth/register", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ username, password }),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Registrierung fehlgeschlagen."));
  }

  return (await response.json()) as PublicUser;
}

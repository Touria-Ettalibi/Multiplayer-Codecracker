import { authHeaders } from "./auth-storage";

export type LobbyUser = {
  id: number;
  username: string;
};

/** Loads the current lobby snapshot (who's online right now). */
export async function fetchLobby(): Promise<LobbyUser[]> {
  const response = await fetch("/api/lobby", { headers: authHeaders() });

  if (!response.ok) {
    throw new Error("Lobby konnte nicht geladen werden.");
  }

  return (await response.json()) as LobbyUser[];
}

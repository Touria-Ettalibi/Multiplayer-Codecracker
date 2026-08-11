import { authHeaders } from "./auth-storage";
import type { InvitePlayerStatus, LobbyUser } from "./lobby-socket";

export type InvitePhase =
  | { phase: "NONE" }
  | { phase: "INVITE_PENDING"; initiator: LobbyUser; deadlineAt: number; players: InvitePlayerStatus[] }
  | { phase: "GAME_RUNNING"; gameId: number; playerUserIds: number[] };

/** Loads the current lobby-wide phase — used to recover state on page load/refresh. */
export async function fetchInvitePhase(): Promise<InvitePhase> {
  const response = await fetch("/api/lobby/invite", { headers: authHeaders() });

  if (!response.ok) {
    throw new Error("Spielstatus konnte nicht geladen werden.");
  }

  return (await response.json()) as InvitePhase;
}

/** Starts a new invite round, inviting everyone currently in the lobby. */
export async function startInvite(): Promise<void> {
  const response = await fetch("/api/lobby/invite/start", {
    method: "POST",
    headers: authHeaders(),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Spiel konnte nicht gestartet werden.");
  }
}

/** Confirms or declines readiness for the current invite. */
export async function respondToInvite(ready: boolean): Promise<void> {
  const response = await fetch("/api/lobby/invite/respond", {
    method: "POST",
    headers: { ...authHeaders(), "content-type": "application/json" },
    body: JSON.stringify({ ready }),
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new Error(body?.error ?? "Antwort konnte nicht übermittelt werden.");
  }
}

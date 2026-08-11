import { authHeaders } from "./auth-storage";

export type HistoryEntry = {
  roundNumber: number;
  guessedCode: string | null;
  correctPosition: number | null;
  correctColor: number | null;
  isValid: boolean;
};

export type ResultRow = {
  userId: number;
  username: string;
  result: "WIN" | "DRAW" | "LOSS" | "PENDING";
  pointsThisGame: number;
  totalPoints: number;
};

export type GameSnapshot = {
  id: number;
  status: "WAITING" | "IN_PROGRESS" | "FINISHED";
  maxRounds: number;
  roundTimeLimitSeconds: number;
  myResult: "WIN" | "DRAW" | "LOSS" | "PENDING";
  history: HistoryEntry[];
  roundNumber?: number;
  roundStartedAt?: number;
  roundEnded?: boolean;
  results?: ResultRow[];
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

/** Loads the current state of one game, including the caller's own guess history. */
export async function fetchGame(gameId: number): Promise<GameSnapshot> {
  const response = await fetch(`/api/games/${gameId}`, { headers: authHeaders() });

  if (!response.ok) {
    throw new Error(await readError(response, "Spiel konnte nicht geladen werden."));
  }

  return (await response.json()) as GameSnapshot;
}

/**
 * TEMPORARY dev-only helper: starts a game directly, standing in for the
 * real ready-check flow until feature/game-invite is merged. Not meant to
 * survive that merge — see backend GameController's Javadoc on
 * /api/games/dev-start.
 */
export async function devStartGame(userIds: number[]): Promise<{ id: number }> {
  const response = await fetch("/api/games/dev-start", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...authHeaders() },
    body: JSON.stringify({ userIds }),
  });

  if (!response.ok) {
    throw new Error(await readError(response, "Spiel konnte nicht gestartet werden."));
  }

  return (await response.json()) as { id: number };
}

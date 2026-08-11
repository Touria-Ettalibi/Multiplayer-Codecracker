import { getToken } from "./auth-storage";
import type { LobbyUser } from "./lobby-api";

export type { LobbyUser };

/** One invited player's current answer to "Möchtest du teilnehmen?". */
export type InvitePlayerStatus = {
  id: number;
  username: string;
  status: "pending" | "ready" | "declined";
};

export type LobbyEvent =
  | { type: "player-joined"; payload: LobbyUser }
  | { type: "player-left"; payload: LobbyUser }
  | {
      type: "game-invite-started";
      payload: { initiator: LobbyUser; deadlineAt: number; players: InvitePlayerStatus[] };
    }
  | {
      type: "game-invite-status";
      payload: { id: number; username: string; status: "ready" | "declined" };
    }
  | { type: "game-invite-cancelled"; payload: { reason: string } }
  | { type: "game-started"; payload: { gameId: number; playerUserIds: number[] } }
  | { type: "lobby-game-ended"; payload: { gameId: number } };

type LobbyEventListener = (event: LobbyEvent) => void;

export type LobbyConnectionStatus = "connecting" | "connected" | "disconnected" | "unreachable";

let socket: WebSocket | null = null;
let reconnectDelayMs = 1000;
const MAX_RECONNECT_DELAY_MS = 15000;

/**
 * Opens the lobby WebSocket and starts delivering join/leave events to the
 * given listener. Safe to call once per page; automatically reconnects
 * with backoff if the connection drops (e.g. brief network hiccup) as long
 * as a token is still stored.
 *
 * @param onEvent        called for every player-joined/player-left event
 * @param onStatusChange optional; called whenever the connection state
 *                        changes, e.g. to drive a status badge in the UI
 */
export function connectLobbySocket(
  onEvent: LobbyEventListener,
  onStatusChange?: (status: LobbyConnectionStatus) => void
): void {
  const token = getToken();
  if (!token) return;

  onStatusChange?.("connecting");

  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  const url = `${protocol}://${window.location.host}/ws/lobby?token=${encodeURIComponent(token)}`;

  socket = new WebSocket(url);

  socket.addEventListener("message", (messageEvent) => {
    try {
      const event = JSON.parse(messageEvent.data as string) as LobbyEvent;
      onEvent(event);
    } catch {
      // ignore malformed messages
    }
  });

  socket.addEventListener("open", () => {
    reconnectDelayMs = 1000;
    onStatusChange?.("connected");
  });

  socket.addEventListener("close", () => {
    if (!getToken()) return; // logged out — don't reconnect
    onStatusChange?.(reconnectDelayMs > 1000 ? "unreachable" : "disconnected");
    setTimeout(() => connectLobbySocket(onEvent, onStatusChange), reconnectDelayMs);
    reconnectDelayMs = Math.min(reconnectDelayMs * 2, MAX_RECONNECT_DELAY_MS);
  });
}

/** Closes the lobby WebSocket, e.g. on logout. */
export function disconnectLobbySocket(): void {
  socket?.close();
  socket = null;
}

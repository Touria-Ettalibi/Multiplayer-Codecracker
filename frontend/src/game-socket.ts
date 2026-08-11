import { getToken } from "./auth-storage";

export type GameEvent =
  | { type: "round-started"; payload: { roundNumber: number; roundStartedAt: number; roundTimeLimitSeconds: number } }
  | { type: "round-ended"; payload: { roundNumber: number } }
  | { type: "player-forfeited"; payload: { userId: number; username: string } }
  | { type: "game-ended"; payload: { results: import("./game-api").ResultRow[] } }
  | { type: "guess-result"; payload: GuessResultPayload };

export type GuessResultPayload = {
  isValid: boolean;
  guessedCode: string | null;
  correctPosition: number | null;
  correctColor: number | null;
  submittedAt: number;
};

type PendingRequest = {
  resolve: (payload: unknown) => void;
  reject: (error: Error) => void;
};

export class GameSocket {
  private socket: WebSocket | null = null;
  private nextRequestId = 1;
  private readonly pending = new Map<string, PendingRequest>();
  private reconnectDelayMs = 1000;
  private closedByCaller = false;
  private readonly gameId: number;
  private readonly onEvent: (event: GameEvent) => void;

  constructor(gameId: number, onEvent: (event: GameEvent) => void) {
    this.gameId = gameId;
    this.onEvent = onEvent;
    this.connect();
  }

  private connect(): void {
    const token = getToken();
    if (!token) return;

    const protocol = window.location.protocol === "https:" ? "wss" : "ws";
    const url = `${protocol}://${window.location.host}/ws/game?gameId=${this.gameId}&token=${encodeURIComponent(token)}`;

    this.socket = new WebSocket(url);

    this.socket.addEventListener("open", () => {
      this.reconnectDelayMs = 1000;
    });

    this.socket.addEventListener("message", (messageEvent) => {
      this.handleMessage(messageEvent.data as string);
    });

    this.socket.addEventListener("close", () => {
      if (this.closedByCaller || !getToken()) return;
      setTimeout(() => this.connect(), this.reconnectDelayMs);
      this.reconnectDelayMs = Math.min(this.reconnectDelayMs * 2, 15000);
    });
  }

  private handleMessage(raw: string): void {
    let message: { type: string; requestId?: string; payload?: unknown; error?: string };
    try {
      message = JSON.parse(raw);
    } catch {
      return;
    }

    if (message.type === "response" && message.requestId) {
      const pendingRequest = this.pending.get(message.requestId);
      if (!pendingRequest) return;
      this.pending.delete(message.requestId);

      if (message.error) {
        pendingRequest.reject(new Error(message.error));
      } else {
        pendingRequest.resolve(message.payload);
      }
      return;
    }

    this.onEvent(message as GameEvent);
  }

  /** Sends a command and resolves once the server responds to this exact request. */
  sendCommand<T>(type: string, payload: Record<string, unknown> = {}): Promise<T> {
    return new Promise((resolve, reject) => {
      if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
        reject(new Error("Keine Verbindung zum Spiel."));
        return;
      }

      const requestId = String(this.nextRequestId++);
      this.pending.set(requestId, { resolve: resolve as (payload: unknown) => void, reject });

      this.socket.send(JSON.stringify({ type, requestId, payload }));
    });
  }

  close(): void {
    this.closedByCaller = true;
    this.socket?.close();
    this.socket = null;
  }
}

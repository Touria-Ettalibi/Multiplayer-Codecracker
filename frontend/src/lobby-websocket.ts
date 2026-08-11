export type LobbyUser = {
  id: number;
  username: string;
};

export type LobbyEvent =
  | { type: "online-list"; users: LobbyUser[] }
  | { type: "join"; user: LobbyUser }
  | { type: "leave"; user: LobbyUser };

function isLobbyUser(value: unknown): value is LobbyUser {
  if (typeof value !== "object" || value === null) return false;

  const candidate = value as Record<string, unknown>;
  return typeof candidate.id === "number" && typeof candidate.username === "string";
}

function isLobbyUserArray(value: unknown): value is LobbyUser[] {
  return Array.isArray(value) && value.every(isLobbyUser);
}

type LobbyWebSocketCallbacks = {
  onEvent: (event: LobbyEvent) => void;
  onStatusChange: (text: string, bootstrapClass: string) => void;
};

/**
 * Manages the Lobby WebSocket connection and forwards presence events
 * (online-list snapshots, joins, leaves) to the caller.
 */
export class LobbyWebSocketClient {
  private readonly callbacks: LobbyWebSocketCallbacks;
  private socket: WebSocket | null = null;

  public constructor(callbacks: LobbyWebSocketCallbacks) {
    this.callbacks = callbacks;
  }

  /** Opens the Lobby WebSocket connection, authenticated with the given JWT. */
  public connect(token: string): void {
    if (this.socket?.readyState === WebSocket.OPEN) return;

    this.callbacks.onStatusChange("Lobby verbindet …", "text-bg-warning");
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(
      `${protocol}//${window.location.host}/ws/lobby?token=${encodeURIComponent(token)}`,
    );
    this.socket = socket;

    socket.addEventListener("open", () => {
      this.callbacks.onStatusChange("Lobby verbunden", "text-bg-success");
    });

    socket.addEventListener("message", (event) => {
      this.handleMessage(String(event.data));
    });

    socket.addEventListener("close", () => {
      if (this.socket === socket) this.socket = null;
      this.callbacks.onStatusChange("Lobby getrennt", "text-bg-secondary");
    });

    socket.addEventListener("error", () => {
      this.callbacks.onStatusChange("Lobby nicht erreichbar", "text-bg-danger");
    });
  }

  /** Closes the Lobby WebSocket connection, e.g. when leaving the dashboard. */
  public disconnect(): void {
    this.socket?.close();
    this.socket = null;
  }

  private handleMessage(messageText: string): void {
    try {
      const message = JSON.parse(messageText) as Record<string, unknown>;

      if (message.type === "online-list" && isLobbyUserArray(message.users)) {
        this.callbacks.onEvent({ type: "online-list", users: message.users });
        return;
      }

      if ((message.type === "join" || message.type === "leave") && isLobbyUser(message.user)) {
        this.callbacks.onEvent({ type: message.type, user: message.user });
      }
    } catch {
      // Ignore malformed messages; the connection stays open.
    }
  }
}

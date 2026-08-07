import {isTodo, type Todo} from "./todo-api";

export type TodoEvent =
  | {type: "created" | "updated"; todo: Todo}
  | {type: "deleted"; id: number};

type PendingRequest = {
  resolve: (payload: unknown) => void;
  reject: (error: Error) => void;
};

type TodoWebSocketCallbacks = {
  onEvent: (event: TodoEvent) => void;
  onStatusChange: (text: string, bootstrapClass: string) => void;
  onInvalidMessage: () => void;
};

/**
 * Manages the Todo WebSocket connection and matches command responses by request ID.
 */
export class TodoWebSocketClient {
  private readonly callbacks: TodoWebSocketCallbacks;
  private socket: WebSocket | null = null;
  private socketReady: Promise<WebSocket> | null = null;
  private nextRequestId = 1;
  private readonly pendingRequests = new Map<string, PendingRequest>();

  public constructor(callbacks: TodoWebSocketCallbacks) {
    this.callbacks = callbacks;
  }

  /** Opens the WebSocket connection or returns the existing connection. */
  public connect(): Promise<WebSocket> {
    if (this.socket?.readyState === WebSocket.OPEN) {
      return Promise.resolve(this.socket);
    }

    if (this.socketReady) return this.socketReady;

    this.callbacks.onStatusChange("WebSocket verbindet …", "text-bg-warning");
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const socket = new WebSocket(`${protocol}//${window.location.host}/ws/todos`);
    this.socket = socket;

    socket.addEventListener("message", (event) => {
      this.handleMessage(String(event.data));
    });

    socket.addEventListener("close", () => {
      this.handleClose(socket);
    });

    this.socketReady = new Promise<WebSocket>((resolve, reject) => {
      socket.addEventListener("open", () => {
        this.callbacks.onStatusChange("WebSocket verbunden", "text-bg-success");
        resolve(socket);
      });

      socket.addEventListener("error", () => {
        reject(new Error("WebSocket-Verbindung konnte nicht hergestellt werden."));
      });
    });

    return this.socketReady;
  }

  /** Sends a command and returns the payload of its matching response. */
  public async sendCommand(type: string, payload: object = {}): Promise<unknown> {
    const socket = await this.connect();
    const requestId = String(this.nextRequestId++);

    return new Promise((resolve, reject) => {
      this.pendingRequests.set(requestId, {resolve, reject});
      socket.send(JSON.stringify({type, requestId, payload}));
    });
  }

  private handleMessage(messageText: string): void {
    try {
      const message = JSON.parse(messageText) as Record<string, unknown>;

      if (message.type === "response") {
        this.handleCommandResponse(message);
        return;
      }

      if ((message.type === "created" || message.type === "updated") && isTodo(message.payload)) {
        this.callbacks.onEvent({type: message.type, todo: message.payload});
        return;
      }

      if (message.type === "deleted" && typeof message.payload === "object" && message.payload !== null) {
        const payload = message.payload as Record<string, unknown>;
        if (typeof payload.id === "number") {
          this.callbacks.onEvent({type: "deleted", id: payload.id});
        }
      }
    } catch {
      this.callbacks.onInvalidMessage();
    }
  }

  private handleCommandResponse(message: Record<string, unknown>): void {
    if (typeof message.requestId !== "string") return;

    const pendingRequest = this.pendingRequests.get(message.requestId);
    if (!pendingRequest) return;

    this.pendingRequests.delete(message.requestId);

    if (typeof message.error === "string") {
      pendingRequest.reject(new Error(message.error));
      return;
    }

    pendingRequest.resolve(message.payload);
  }

  private handleClose(socket: WebSocket): void {
    if (this.socket === socket) this.socket = null;
    this.socketReady = null;
    this.callbacks.onStatusChange("WebSocket getrennt", "text-bg-secondary");

    for (const request of this.pendingRequests.values()) {
      request.reject(new Error("Die WebSocket-Verbindung wurde getrennt."));
    }
    this.pendingRequests.clear();
  }
}

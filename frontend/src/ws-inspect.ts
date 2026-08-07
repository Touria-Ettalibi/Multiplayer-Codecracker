import 'bootstrap/dist/js/bootstrap.bundle.min.js';

const form = document.querySelector<HTMLFormElement>("#ws-form");
const pathInput = document.querySelector<HTMLInputElement>("#ws-path");
const connectButton = document.querySelector<HTMLButtonElement>("#connect-button");
const disconnectButton = document.querySelector<HTMLButtonElement>("#disconnect-button");
const clearLogButton = document.querySelector<HTMLButtonElement>("#clear-log-button");
const statusBadge = document.querySelector<HTMLSpanElement>("#status-badge");
const errorBox = document.querySelector<HTMLDivElement>("#ws-error");
const logBox = document.querySelector<HTMLDivElement>("#ws-log");

let socket: WebSocket | null = null;

function showError(message: string) {
  if (!errorBox) return;

  errorBox.textContent = message;
  errorBox.hidden = false;
}

function clearError() {
  if (!errorBox) return;

  errorBox.textContent = "";
  errorBox.hidden = true;
}

function setStatus(text: string, className: string) {
  if (!statusBadge) return;

  statusBadge.className = `badge ${className}`;
  statusBadge.textContent = text;
}

function setConnectedState(isConnected: boolean) {
  if (connectButton) connectButton.disabled = isConnected;
  if (disconnectButton) disconnectButton.disabled = !isConnected;
  if (pathInput) pathInput.disabled = isConnected;
}

function createWebSocketUrl(path: string): string {
  const normalizedPath = path.startsWith("/") ? path : `/${path}`;
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";

  return `${protocol}//${window.location.host}${normalizedPath}`;
}

function appendLog(type: string, message: string) {
  if (!logBox) return;

  const entry = document.createElement("div");
  const timestamp = new Date().toLocaleTimeString();

  entry.className = "border-bottom border-secondary-subtle py-2";
  entry.textContent = `[${timestamp}] ${type}: ${message}`;

  logBox.appendChild(entry);
  logBox.scrollTop = logBox.scrollHeight;
}

function connect(path: string) {
  clearError();

  if (socket && socket.readyState !== WebSocket.CLOSED) {
    socket.close();
  }

  const url = createWebSocketUrl(path);

  socket = new WebSocket(url);

  setStatus("Verbinde ...", "text-bg-warning");
  setConnectedState(true);
  appendLog("SYSTEM", `Verbinde mit ${url}`);

  socket.addEventListener("open", () => {
    setStatus("Verbunden", "text-bg-success");
    appendLog("SYSTEM", "Verbindung geöffnet");
  });

  socket.addEventListener("message", (event) => {
    appendLog("SERVER", String(event.data));
  });

  socket.addEventListener("error", () => {
    showError("WebSocket-Fehler. Prüfe den Pfad oder das Backend.");
    setStatus("Fehler", "text-bg-danger");
    appendLog("ERROR", "WebSocket-Fehler");
  });

  socket.addEventListener("close", (event) => {
    setStatus("Nicht verbunden", "text-bg-secondary");
    setConnectedState(false);

    appendLog(
      "SYSTEM",
      `Verbindung geschlossen. Code: ${event.code}${
        event.reason ? `, Grund: ${event.reason}` : ""
      }`,
    );

    socket = null;
  });
}

function disconnect() {
  if (!socket) return;

  appendLog("SYSTEM", "Verbindung wird getrennt");
  socket.close();
}

form?.addEventListener("submit", (event) => {
  event.preventDefault();

  const path = pathInput?.value.trim() || "/ws/todos";
  connect(path);
});

disconnectButton?.addEventListener("click", () => {
  disconnect();
});

clearLogButton?.addEventListener("click", () => {
  if (logBox) logBox.innerHTML = "";
});

setStatus("Nicht verbunden", "text-bg-secondary");
setConnectedState(false);

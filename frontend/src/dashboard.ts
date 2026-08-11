import { getToken, isLoggedIn } from "./auth-storage";
import { LobbyWebSocketClient, type LobbyEvent, type LobbyUser } from "./lobby-websocket";

const listElement = document.querySelector<HTMLUListElement>("#online-players-list");
const statusElement = document.querySelector<HTMLSpanElement>("#lobby-status");

let onlineUsers: LobbyUser[] = [];

function setStatus(text: string, bootstrapClass: string): void {
  if (!statusElement) return;
  statusElement.textContent = text;
  statusElement.className = `badge ${bootstrapClass}`;
}

function renderOnlineUsers(): void {
  if (!listElement) return;
  listElement.replaceChildren();

  if (onlineUsers.length === 0) {
    const emptyState = document.createElement("li");
    emptyState.className = "list-group-item text-center text-body-secondary py-4";
    emptyState.textContent = "Niemand online.";
    listElement.appendChild(emptyState);
    return;
  }

  for (const user of onlineUsers) {
    const item = document.createElement("li");
    item.className = "list-group-item";
    item.textContent = user.username;
    listElement.appendChild(item);
  }
}

function addUser(user: LobbyUser): void {
  if (onlineUsers.some((existing) => existing.id === user.id)) return;
  onlineUsers = [...onlineUsers, user];
  renderOnlineUsers();
}

function removeUser(userId: number): void {
  onlineUsers = onlineUsers.filter((user) => user.id !== userId);
  renderOnlineUsers();
}

function handleLobbyEvent(event: LobbyEvent): void {
  if (event.type === "online-list") {
    onlineUsers = event.users;
    renderOnlineUsers();
    return;
  }

  if (event.type === "join") {
    addUser(event.user);
    return;
  }

  removeUser(event.user.id);
}

// require-auth.ts (loaded before this script, see dashboard.html) already
// redirects unauthenticated users away from this page; this check just
// avoids connecting the socket during the brief window before that
// redirect takes effect.
if (isLoggedIn()) {
  const token = getToken();

  if (token) {
    const lobbySocket = new LobbyWebSocketClient({
      onEvent: handleLobbyEvent,
      onStatusChange: setStatus,
    });

    lobbySocket.connect(token);
  }
}

// NOTE: hiding the lobby view during an active game (per the acceptance
// criteria) isn't wired up yet — it depends on a game-state signal that
// doesn't exist until the Game feature is built. Once that exists, wrap
// the markup below in a #lobby-view container and toggle it alongside a
// #game-view container based on that signal.

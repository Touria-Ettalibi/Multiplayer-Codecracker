import { fetchLobby, type LobbyUser } from "./lobby-api";
import { fetchInvitePhase, respondToInvite, startInvite, type InvitePhase } from "./lobby-invite-api";
import {
  connectLobbySocket,
  type InvitePlayerStatus,
  type LobbyConnectionStatus,
  type LobbyEvent,
} from "./lobby-socket";
import { getClaims } from "./auth-storage";

const listElement = document.querySelector<HTMLUListElement>("#online-players-list");
const subtitleElement = document.querySelector<HTMLElement>("#lobbySubtitle");
const statusElement = document.querySelector<HTMLSpanElement>("#lobby-status");
const errorBox = document.querySelector<HTMLDivElement>("#lobbyError");

const inviteBanner = document.querySelector<HTMLDivElement>("#inviteBanner");
const inviteBannerTitle = document.querySelector<HTMLElement>("#inviteBannerTitle");
const inviteAcceptButton = document.querySelector<HTMLButtonElement>("#inviteAcceptButton");
const inviteDeclineButton = document.querySelector<HTMLButtonElement>("#inviteDeclineButton");

const gameRunningBanner = document.querySelector<HTMLDivElement>("#gameRunningBanner");
const startGameButton = document.querySelector<HTMLButtonElement>("#startGameButton");

const currentUserId = getClaims()?.uid;

// The two sources of truth this page combines:
// - onlineUsers: who's connected to the lobby right now (player-joined/-left).
// - phase: what the *lobby as a whole* is doing right now — nothing, an
//   invite in progress, or a game already running. Independent of who's
//   online, since a game can keep running after someone who isn't in it
//   is the only one left connected.
const onlineUsers = new Map<number, LobbyUser>();
let phase: InvitePhase = { phase: "NONE" };
let countdownIntervalId: number | undefined;

function showError(message: string): void {
  if (!errorBox) return;
  errorBox.textContent = message;
  errorBox.classList.remove("d-none");
}

function setConnectionStatus(status: LobbyConnectionStatus): void {
  if (!statusElement) return;

  const byStatus: Record<LobbyConnectionStatus, [text: string, bootstrapClass: string]> = {
    connecting: ["Lobby verbindet …", "text-bg-warning"],
    connected: ["Lobby verbunden", "text-bg-success"],
    disconnected: ["Lobby getrennt", "text-bg-secondary"],
    unreachable: ["Lobby nicht erreichbar", "text-bg-danger"],
  };

  const [text, bootstrapClass] = byStatus[status];
  statusElement.textContent = text;
  statusElement.className = `badge ${bootstrapClass}`;
}

/** Looks up the current user's own entry in an invite's player list, if any. */
function myInviteEntry(players: InvitePlayerStatus[]): InvitePlayerStatus | undefined {
  return players.find((player) => player.id === currentUserId);
}

function render(): void {
  renderSubtitle();
  renderPlayerList();
  renderInviteBanner();
  renderGameRunningBanner();
  renderStartButton();
}

function renderSubtitle(): void {
  if (!subtitleElement) return;

  if (phase.phase === "INVITE_PENDING") {
    const remainingSeconds = Math.max(0, Math.round((phase.deadlineAt - Date.now()) / 1000));
    subtitleElement.textContent = `Aktuell angemeldete Benutzer · Bereitschaft ${remainingSeconds} s`;
    return;
  }

  subtitleElement.textContent = "Aktuell angemeldete Benutzer";
}

function renderPlayerList(): void {
  if (!listElement) return;
  listElement.replaceChildren();

  const users = [...onlineUsers.values()].sort((a, b) => a.username.localeCompare(b.username));

  for (const user of users) {
    const item = document.createElement("li");
    item.className = "list-group-item d-flex justify-content-between align-items-center";

    const label = document.createElement("span");
    label.textContent = user.id === currentUserId ? `${user.username} · du` : user.username;
    item.append(label);

    const statusText = playerStatusText(user.id);
    if (statusText) {
      const status = document.createElement("span");
      status.className = "text-body-secondary small";
      status.textContent = statusText;
      item.append(status);
    }

    listElement.appendChild(item);
  }
}

/** The little "ausstehend"/"bereit"/"abgelehnt"/"spielt" label next to a player, if any. */
function playerStatusText(userId: number): string | null {
  if (phase.phase === "INVITE_PENDING") {
    const entry = phase.players.find((player) => player.id === userId);
    if (entry?.status === "ready") return "bereit";
    if (entry?.status === "declined") return "abgelehnt";
    if (entry) return "ausstehend";
    return null;
  }

  if (phase.phase === "GAME_RUNNING" && phase.playerUserIds.includes(userId)) {
    return "spielt";
  }

  return null;
}

function renderInviteBanner(): void {
  if (!inviteBanner || !inviteBannerTitle) return;

  if (phase.phase !== "INVITE_PENDING") {
    inviteBanner.classList.add("d-none");
    return;
  }

  const myEntry = myInviteEntry(phase.players);
  const alreadyResponded = myEntry?.status === "ready" || myEntry?.status === "declined";

  if (!myEntry || alreadyResponded) {
    inviteBanner.classList.add("d-none");
    return;
  }

  inviteBannerTitle.textContent = `${phase.initiator.username} startet ein Spiel.`;
  inviteBanner.classList.remove("d-none");
}

function renderGameRunningBanner(): void {
  if (!gameRunningBanner) return;
  gameRunningBanner.classList.toggle("d-none", phase.phase !== "GAME_RUNNING");
}

function renderStartButton(): void {
  if (!startGameButton) return;
  startGameButton.disabled = phase.phase !== "NONE";
}

function startCountdown(): void {
  stopCountdown();
  countdownIntervalId = window.setInterval(() => {
    if (phase.phase !== "INVITE_PENDING") {
      stopCountdown();
      return;
    }
    renderSubtitle();
  }, 1000);
}

function stopCountdown(): void {
  if (countdownIntervalId !== undefined) {
    window.clearInterval(countdownIntervalId);
    countdownIntervalId = undefined;
  }
}

function setPhase(next: InvitePhase): void {
  phase = next;
  if (phase.phase === "INVITE_PENDING") {
    startCountdown();
  } else {
    stopCountdown();
  }
  render();
}

function applyLobbyEvent(event: LobbyEvent): void {
  switch (event.type) {
    case "player-joined":
      onlineUsers.set(event.payload.id, event.payload);
      break;
    case "player-left":
      onlineUsers.delete(event.payload.id);
      break;
    case "game-invite-started":
      setPhase({ phase: "INVITE_PENDING", ...event.payload });
      return;
    case "game-invite-status": {
      if (phase.phase !== "INVITE_PENDING") return;
      const players = phase.players.map((player) =>
        player.id === event.payload.id ? { ...player, status: event.payload.status } : player
      );
      setPhase({ ...phase, players });
      return;
    }
    case "game-invite-cancelled":
      setPhase({ phase: "NONE" });
      return;
    case "game-started":
      setPhase({ phase: "GAME_RUNNING", gameId: event.payload.gameId, playerUserIds: event.payload.playerUserIds });
      if (event.payload.playerUserIds.includes(currentUserId ?? -1)) {
        window.location.href = `/pages/game.html?id=${event.payload.gameId}`;
      }
      return;
    case "lobby-game-ended":
      setPhase({ phase: "NONE" });
      return;
  }

  render();
}

async function onStartGameClick(): Promise<void> {
  if (!startGameButton) return;

  startGameButton.disabled = true;
  try {
    await startInvite();
    // The resulting phase arrives via the "game-invite-started" WebSocket
    // event above (broadcast to every lobby client, including this one) —
    // no need to apply it from the REST response too.
  } catch (error) {
    showError(error instanceof Error ? error.message : "Spiel konnte nicht gestartet werden.");
    renderStartButton(); // re-enable if we're still in the NONE phase
  }
}

async function onInviteRespond(ready: boolean): Promise<void> {
  try {
    await respondToInvite(ready);
  } catch (error) {
    showError(error instanceof Error ? error.message : "Antwort konnte nicht übermittelt werden.");
  }
}

startGameButton?.addEventListener("click", () => void onStartGameClick());
inviteAcceptButton?.addEventListener("click", () => void onInviteRespond(true));
inviteDeclineButton?.addEventListener("click", () => void onInviteRespond(false));

async function init(): Promise<void> {
  try {
    const [users, invitePhase] = await Promise.all([fetchLobby(), fetchInvitePhase()]);
    for (const user of users) {
      onlineUsers.set(user.id, user);
    }
    setPhase(invitePhase);
  } catch (error) {
    showError(error instanceof Error ? error.message : "Lobby konnte nicht geladen werden.");
    render();
  }

  connectLobbySocket(applyLobbyEvent, setConnectionStatus);
}

void init();

import { fetchGame, type ResultRow } from "./game-api";
import { GameSocket, type GameEvent } from "./game-socket";

type ColorCode = "R" | "B" | "G" | "Y";

const COLORS: { code: ColorCode; hex: string; label: string }[] = [
  { code: "R", hex: "#dc3545", label: "Rot" },
  { code: "B", hex: "#0d6efd", label: "Blau" },
  { code: "G", hex: "#198754", label: "Grün" },
  { code: "Y", hex: "#ffc107", label: "Gelb" },
];

function colorHex(code: string | null): string {
  return COLORS.find((c) => c.code === code)?.hex ?? "#adb5bd";
}

// --- DOM references -------------------------------------------------------

const gameErrorBox = document.getElementById("gameError") as HTMLDivElement;
const gameInfoBox = document.getElementById("gameInfo") as HTMLDivElement;
const activeRoundView = document.getElementById("activeRoundView") as HTMLDivElement;
const gameOverView = document.getElementById("gameOverView") as HTMLDivElement;

const roundLabel = document.getElementById("roundLabel") as HTMLElement;
const timeLabel = document.getElementById("timeLabel") as HTMLElement;
const colorPalette = document.getElementById("colorPalette") as HTMLDivElement;
const positionSlots = document.getElementById("positionSlots") as HTMLDivElement;
const confirmButton = document.getElementById("confirmButton") as HTMLButtonElement;
const forfeitButton = document.getElementById("forfeitButton") as HTMLButtonElement;
const historyList = document.getElementById("historyList") as HTMLDivElement;

const gameOverHeadline = document.getElementById("gameOverHeadline") as HTMLElement;
const gameOverSubline = document.getElementById("gameOverSubline") as HTMLElement;
const resultsTableBody = document.getElementById("resultsTableBody") as HTMLTableSectionElement;
const closeGameOverButton = document.getElementById("closeGameOverButton") as HTMLButtonElement;

// --- State -----------------------------------------------------------------

const gameId = Number(new URLSearchParams(window.location.search).get("id"));

let maxRounds = 10;
let roundTimeLimitSeconds = 30;
let roundStartedAt = 0;
let currentRoundNumber = 1;
let lastRoundNumber = 1;

let positions: (ColorCode | null)[] = [null, null, null, null];
let selectedColor: ColorCode | null = null;
let codeLocked = false;
let autoSubmittedThisRound = false;

let countdownInterval: ReturnType<typeof setInterval> | undefined;
let socket: GameSocket | null = null;

// --- Rendering ---------------------------------------------------------

function showError(message: string): void {
  gameErrorBox.textContent = message;
  gameErrorBox.classList.remove("d-none");
}

function showInfo(message: string): void {
  gameInfoBox.textContent = message;
  gameInfoBox.classList.remove("d-none");
  setTimeout(() => gameInfoBox.classList.add("d-none"), 4000);
}

function renderPalette(): void {
  colorPalette.replaceChildren(
    ...COLORS.map((color) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "color-swatch" + (selectedColor === color.code ? " selected" : "");
      button.style.background = color.hex;
      button.title = color.label;
      button.disabled = codeLocked;
      button.addEventListener("click", () => {
        selectedColor = color.code;
        renderPalette();
      });
      return button;
    })
  );
}

function renderSlots(): void {
  positionSlots.replaceChildren(
    ...positions.map((value, index) => {
      const slot = document.createElement("button");
      slot.type = "button";
      slot.className = "position-slot" + (value ? " filled" : "");
      slot.style.width = "60px";
      if (value) {
        slot.style.background = colorHex(value);
      }
      slot.disabled = codeLocked;
      slot.addEventListener("click", () => {
        if (codeLocked) return;
        if (positions[index]) {
          positions[index] = null; // click a filled slot to clear it before confirming
        } else if (selectedColor) {
          positions[index] = selectedColor;
        }
        renderSlots();
        updateConfirmButton();
      });
      return slot;
    })
  );
}

function updateConfirmButton(): void {
  const isComplete = positions.every((value) => value !== null);
  confirmButton.disabled = codeLocked || !isComplete;
}

function renderHistoryEntry(entry: {
  roundNumber: number;
  guessedCode: string | null;
  correctPosition: number | null;
  correctColor: number | null;
  isValid: boolean;
}): void {
  const row = document.createElement("div");
  row.className = "history-row";

  const codeDots = entry.isValid && entry.guessedCode
    ? entry.guessedCode.split("").map((c) => {
        const dot = document.createElement("span");
        dot.className = "history-code-dot";
        dot.style.background = colorHex(c);
        return dot;
      })
    : [document.createTextNode("Kein gültiger Versuch")];

  const title = document.createElement("div");
  title.className = "fw-bold mb-2";
  title.textContent = `Runde ${entry.roundNumber}`;

  const codeRow = document.createElement("div");
  codeRow.className = "d-flex align-items-center justify-content-between";

  const dotsContainer = document.createElement("div");
  dotsContainer.append(...codeDots);

  const feedback = document.createElement("div");
  if (entry.isValid) {
    feedback.innerHTML =
      `<span class="feedback-dot feedback-dot-exact"></span>${entry.correctPosition} ` +
      `<span class="feedback-dot feedback-dot-color ms-2"></span>${entry.correctColor}`;
  }

  codeRow.append(dotsContainer, feedback);
  row.append(title, codeRow);
  historyList.prepend(row); // newest round on top, matching the mockup
}

function resetRoundUi(): void {
  positions = [null, null, null, null];
  selectedColor = null;
  codeLocked = false;
  autoSubmittedThisRound = false;
  renderPalette();
  renderSlots();
  updateConfirmButton();
}

function startCountdown(): void {
  clearInterval(countdownInterval);
  countdownInterval = setInterval(() => {
    const elapsedSeconds = (Date.now() - roundStartedAt) / 1000;
    const remaining = Math.max(0, Math.ceil(roundTimeLimitSeconds - elapsedSeconds));
    timeLabel.textContent = `Verbleibende Zeit: ${remaining} s`;

    if (remaining <= 0) {
      autoSubmitOnTimeout();
    }
  }, 250);
}

function autoSubmitOnTimeout(): void {
  if (codeLocked || autoSubmittedThisRound) return;
  autoSubmittedThisRound = true;
  submitCurrentCode();
}

function submitCurrentCode(): void {
  if (codeLocked || !socket) return;
  codeLocked = true;
  renderPalette();
  renderSlots();
  updateConfirmButton();

  const code = positions.filter((value): value is ColorCode => value !== null).join("");

  socket.sendCommand("submit-guess", { code }).catch((error: Error) => {
    showError(error.message);
  });
}

// --- Event handling ---------------------------------------------------

function handleGameEvent(event: GameEvent): void {
  switch (event.type) {
    case "round-started":
      currentRoundNumber = event.payload.roundNumber;
      lastRoundNumber = currentRoundNumber;
      roundStartedAt = event.payload.roundStartedAt;
      roundTimeLimitSeconds = event.payload.roundTimeLimitSeconds;
      roundLabel.textContent = `Runde ${currentRoundNumber} von ${maxRounds}`;
      resetRoundUi();
      startCountdown();
      break;

    case "round-ended":
      lastRoundNumber = event.payload.roundNumber;
      clearInterval(countdownInterval);
      timeLabel.textContent = "Runde beendet";
      break;

    case "player-forfeited":
      showInfo(`${event.payload.username} hat das Spiel abgebrochen.`);
      break;

    case "guess-result":
      renderHistoryEntry({
        roundNumber: currentRoundNumber,
        guessedCode: event.payload.guessedCode,
        correctPosition: event.payload.correctPosition,
        correctColor: event.payload.correctColor,
        isValid: event.payload.isValid,
      });
      break;

    case "game-ended":
      showGameOver(event.payload.results);
      break;
  }
}

function showGameOver(results: ResultRow[]): void {
  clearInterval(countdownInterval);
  socket?.close();

  activeRoundView.classList.add("d-none");
  gameOverView.classList.remove("d-none");

  const winners = results.filter((r) => r.result === "WIN" || r.result === "DRAW");

  if (winners.length === 1) {
    gameOverHeadline.textContent = `${winners[0].username} hat gewonnen`;
    gameOverSubline.textContent = `Der Geheimcode wurde in Runde ${lastRoundNumber} erraten.`;
  } else if (winners.length > 1) {
    gameOverHeadline.textContent = `Unentschieden zwischen ${winners.map((w) => w.username).join(" und ")}`;
    gameOverSubline.textContent = `Der Geheimcode wurde in Runde ${lastRoundNumber} erraten.`;
  } else {
    gameOverHeadline.textContent = "Niemand hat den Code erraten";
    gameOverSubline.textContent = `Das Spiel endete nach ${maxRounds} Runden ohne Gewinner.`;
  }

  const resultLabels: Record<string, string> = { WIN: "Gewonnen", DRAW: "Unentschieden", LOSS: "Verloren", PENDING: "—" };

  resultsTableBody.replaceChildren(
    ...results.map((row) => {
      const tr = document.createElement("tr");
      const pointsSign = row.pointsThisGame > 0 ? "+" : "";
      tr.innerHTML = `
        <td>${row.username}</td>
        <td class="fw-bold">${resultLabels[row.result] ?? row.result}</td>
        <td class="text-end">${pointsSign}${row.pointsThisGame}</td>
        <td class="text-end">${row.totalPoints}</td>
      `;
      return tr;
    })
  );
}

// --- Wiring -------------------------------------------------------------

confirmButton.addEventListener("click", submitCurrentCode);

forfeitButton.addEventListener("click", async () => {
  if (!socket) return;
  const confirmed = window.confirm("Möchtest du das Spiel wirklich abbrechen? Das zählt als Niederlage.");
  if (!confirmed) return;

  try {
    await socket.sendCommand("forfeit");
    window.location.href = "/pages/dashboard.html";
  } catch (error) {
    showError(error instanceof Error ? error.message : "Abbrechen fehlgeschlagen.");
  }
});

closeGameOverButton.addEventListener("click", () => {
  window.location.href = "/pages/dashboard.html";
});

async function init(): Promise<void> {
  if (!gameId || Number.isNaN(gameId)) {
    showError("Ungültige Spiel-ID.");
    return;
  }

  try {
    const snapshot = await fetchGame(gameId);
    maxRounds = snapshot.maxRounds;
    roundTimeLimitSeconds = snapshot.roundTimeLimitSeconds;

    for (const entry of snapshot.history) {
      renderHistoryEntry(entry);
      lastRoundNumber = entry.roundNumber;
    }

    if (snapshot.status === "FINISHED" && snapshot.results) {
      showGameOver(snapshot.results);
      return;
    }

    currentRoundNumber = snapshot.roundNumber ?? 1;
    lastRoundNumber = currentRoundNumber;
    roundStartedAt = snapshot.roundStartedAt ?? Date.now();
    roundLabel.textContent = `Runde ${currentRoundNumber} von ${maxRounds}`;

    activeRoundView.classList.remove("d-none");
    resetRoundUi();
    startCountdown();

    socket = new GameSocket(gameId, handleGameEvent);
  } catch (error) {
    showError(error instanceof Error ? error.message : "Spiel konnte nicht geladen werden.");
  }
}

void init();

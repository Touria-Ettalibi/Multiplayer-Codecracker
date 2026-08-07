import "bootstrap/dist/js/bootstrap.bundle.min.js";
import {
  createTodo as createTodoWithRest,
  deleteTodo as deleteTodoWithRest,
  fetchTodos,
  isTodo,
  isTodoArray,
  type Todo,
  updateTodo as updateTodoWithRest,
} from "./todo-api";
import {TodoWebSocketClient, type TodoEvent} from "./todo-websocket";

type Transport = "rest" | "websocket";

const form = document.querySelector<HTMLFormElement>("#todo-form");
const input = document.querySelector<HTMLInputElement>("#todo-text");
const list = document.querySelector<HTMLUListElement>("#todo-list");
const errorBox = document.querySelector<HTMLParagraphElement>("#todo-error");
const transportInputs = document.querySelectorAll<HTMLInputElement>("input[name='transport']");
const transportHelp = document.querySelector<HTMLDivElement>("#transport-help");
const websocketStatus = document.querySelector<HTMLSpanElement>("#websocket-status");

let todos: Todo[] = [];
let currentTransport: Transport = "rest";

function showError(message: string): void {
  if (!errorBox) return;
  errorBox.textContent = message;
  errorBox.hidden = false;
}

function clearError(): void {
  if (!errorBox) return;
  errorBox.textContent = "";
  errorBox.hidden = true;
}

function setWebSocketStatus(text: string, bootstrapClass: string): void {
  if (!websocketStatus) return;
  websocketStatus.textContent = text;
  websocketStatus.className = `badge ${bootstrapClass}`;
}

function upsertTodo(todo: Todo): void {
  const existingIndex = todos.findIndex((item) => item.id === todo.id);

  if (existingIndex === -1) {
    todos.push(todo);
  } else {
    todos[existingIndex] = todo;
  }

  todos.sort((first, second) => first.id - second.id);
  renderTodos();
}

function removeTodo(id: number): void {
  todos = todos.filter((todo) => todo.id !== id);
  renderTodos();
}

function handleTodoEvent(event: TodoEvent): void {
  if (event.type === "deleted") {
    removeTodo(event.id);
    return;
  }

  upsertTodo(event.todo);
}

const todoWebSocket = new TodoWebSocketClient({
  onEvent: handleTodoEvent,
  onStatusChange: setWebSocketStatus,
  onInvalidMessage: () => {
    showError("Der Server hat eine ungültige WebSocket-Nachricht gesendet.");
  },
});

async function loadTodos(): Promise<void> {
  clearError();

  try {
    const result = currentTransport === "rest"
      ? await fetchTodos()
      : await todoWebSocket.sendCommand("find-all");

    if (!isTodoArray(result)) throw new Error("Der Server hat keine gültige Todoliste gesendet.");
    todos = result;
    renderTodos();
  } catch (error) {
    showError(error instanceof Error ? error.message : "Unbekannter Fehler");
  }
}

async function createTodo(text: string): Promise<Todo> {
  if (currentTransport === "rest") return createTodoWithRest(text);

  const result = await todoWebSocket.sendCommand("create", {text});
  if (!isTodo(result)) throw new Error("Der Server hat kein gültiges Todo gesendet.");
  return result;
}

async function updateTodo(todo: Todo): Promise<Todo> {
  if (currentTransport === "rest") return updateTodoWithRest(todo);

  const result = await todoWebSocket.sendCommand("update", todo);
  if (!isTodo(result)) throw new Error("Der Server hat kein gültiges Todo gesendet.");
  return result;
}

async function deleteTodo(id: number): Promise<void> {
  if (currentTransport === "rest") {
    await deleteTodoWithRest(id);
    return;
  }

  await todoWebSocket.sendCommand("delete", {id});
}

function renderTodos(): void {
  if (!list) return;
  list.replaceChildren();

  if (todos.length === 0) {
    const emptyState = document.createElement("li");
    emptyState.className = "list-group-item text-center text-body-secondary py-5";
    emptyState.textContent = "Noch keine Todos vorhanden.";
    list.appendChild(emptyState);
    return;
  }

  for (const todo of todos) {
    const item = document.createElement("li");
    item.className = "list-group-item d-flex align-items-center gap-3 px-4 py-3";

    const checkbox = document.createElement("input");
    checkbox.type = "checkbox";
    checkbox.checked = todo.done;
    checkbox.className = "form-check-input mt-0 flex-shrink-0";
    checkbox.setAttribute("aria-label", `Todo "${todo.text}" als erledigt markieren`);

    const text = document.createElement("span");
    text.textContent = todo.text;
    text.className = "flex-grow-1";
    if (todo.done) text.classList.add("text-decoration-line-through", "text-body-secondary");

    const deleteButton = document.createElement("button");
    deleteButton.type = "button";
    deleteButton.textContent = "Löschen";
    deleteButton.className = "btn btn-outline-danger btn-sm";

    checkbox.addEventListener("change", async () => {
      clearError();
      try {
        upsertTodo(await updateTodo({...todo, done: checkbox.checked}));
      } catch (error) {
        checkbox.checked = todo.done;
        showError(error instanceof Error ? error.message : "Unbekannter Fehler");
      }
    });

    deleteButton.addEventListener("click", async () => {
      clearError();
      deleteButton.disabled = true;
      try {
        await deleteTodo(todo.id);
        removeTodo(todo.id);
      } catch (error) {
        deleteButton.disabled = false;
        showError(error instanceof Error ? error.message : "Unbekannter Fehler");
      }
    });

    item.append(checkbox, text, deleteButton);
    list.appendChild(item);
  }
}

form?.addEventListener("submit", async (event) => {
  event.preventDefault();
  clearError();

  const text = input?.value.trim();
  if (!text) {
    showError("Bitte gib einen Todo-Text ein.");
    return;
  }

  try {
    upsertTodo(await createTodo(text));
    if (input) {
      input.value = "";
      input.focus();
    }
  } catch (error) {
    showError(error instanceof Error ? error.message : "Unbekannter Fehler");
  }
});

transportInputs.forEach((transportInput) => {
  transportInput.addEventListener("change", () => {
    if (!transportInput.checked) return;
    currentTransport = transportInput.value === "websocket" ? "websocket" : "rest";

    if (transportHelp) {
      transportHelp.textContent = currentTransport === "rest"
        ? "Laden und Ändern erfolgen über die REST-Schnittstelle."
        : "Laden und Ändern erfolgen als Kommandos über den WebSocket.";
    }

    void loadTodos();
  });
});

void todoWebSocket.connect().catch(() => {
  setWebSocketStatus("WebSocket nicht erreichbar", "text-bg-danger");
});
void loadTodos();

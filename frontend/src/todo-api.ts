export type Todo = {
  id: number;
  text: string;
  done: boolean;
  createdAt: number;
  updatedAt: number;
};

export function isTodo(value: unknown): value is Todo {
  if (typeof value !== "object" || value === null) return false;

  const candidate = value as Record<string, unknown>;
  return typeof candidate.id === "number"
    && typeof candidate.text === "string"
    && typeof candidate.done === "boolean"
    && typeof candidate.createdAt === "number"
    && typeof candidate.updatedAt === "number";
}

export function isTodoArray(value: unknown): value is Todo[] {
  return Array.isArray(value) && value.every(isTodo);
}

async function readTodo(response: Response, errorMessage: string): Promise<Todo> {
  if (!response.ok) throw new Error(errorMessage);

  const result: unknown = await response.json();
  if (!isTodo(result)) throw new Error("Die REST-Antwort enthält kein gültiges Todo.");
  return result;
}

/** Loads all Todos through the REST API. */
export async function fetchTodos(): Promise<Todo[]> {
  const response = await fetch("/api/todos");
  if (!response.ok) throw new Error("Todos konnten nicht geladen werden.");

  const result: unknown = await response.json();
  if (!isTodoArray(result)) throw new Error("Die REST-Antwort enthält ungültige Todos.");
  return result;
}

/** Creates a Todo through the REST API. */
export async function createTodo(text: string): Promise<Todo> {
  const response = await fetch("/api/todos", {
    method: "POST",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({text}),
  });

  return readTodo(response, "Todo konnte nicht erstellt werden.");
}

/** Updates a Todo through the REST API. */
export async function updateTodo(todo: Todo): Promise<Todo> {
  const response = await fetch(`/api/todos/${todo.id}`, {
    method: "PUT",
    headers: {"Content-Type": "application/json"},
    body: JSON.stringify({text: todo.text, done: todo.done}),
  });

  return readTodo(response, "Todo konnte nicht aktualisiert werden.");
}

/** Deletes a Todo through the REST API. */
export async function deleteTodo(id: number): Promise<void> {
  const response = await fetch(`/api/todos/${id}`, {method: "DELETE"});
  if (!response.ok) throw new Error("Todo konnte nicht gelöscht werden.");
}

import { Modal } from "bootstrap";
import { createUser, deleteUser, fetchUsers, updateUser, type ManagedUser } from "./user-api";

const tableBody = document.getElementById("usersTableBody") as HTMLTableSectionElement;
const errorBox = document.getElementById("usersError") as HTMLDivElement;
const searchInput = document.getElementById("searchInput") as HTMLInputElement;
const addUserButton = document.getElementById("addUserButton") as HTMLButtonElement;

const userModalElement = document.getElementById("userModal") as HTMLDivElement;
const userModal = new Modal(userModalElement);
const userModalTitle = document.getElementById("userModalTitle") as HTMLElement;
const userForm = document.getElementById("userForm") as HTMLFormElement;
const userFormError = document.getElementById("userFormError") as HTMLDivElement;
const userIdInput = document.getElementById("userId") as HTMLInputElement;
const userUsernameInput = document.getElementById("userUsername") as HTMLInputElement;
const userRoleInput = document.getElementById("userRole") as HTMLSelectElement;
const userPasswordInput = document.getElementById("userPassword") as HTMLInputElement;
const userPasswordHint = document.getElementById("userPasswordHint") as HTMLElement;

let searchDebounce: ReturnType<typeof setTimeout> | undefined;

function showListError(message: string): void {
  errorBox.textContent = message;
  errorBox.classList.remove("d-none");
}

function hideListError(): void {
  errorBox.classList.add("d-none");
}

function showFormError(message: string): void {
  userFormError.textContent = message;
  userFormError.classList.remove("d-none");
}

function hideFormError(): void {
  userFormError.classList.add("d-none");
}

function formatDate(epochMillis: number): string {
  return new Date(epochMillis).toLocaleDateString("de-DE");
}

function renderRow(user: ManagedUser): HTMLTableRowElement {
  const row = document.createElement("tr");

  row.innerHTML = `
    <td>${user.id}</td>
    <td>${user.username}</td>
    <td>${user.role === "ADMIN" ? "Admin" : "User"}</td>
    <td>${formatDate(user.createdAt)}</td>
    <td class="text-end"></td>
  `;

  const actionsCell = row.querySelector("td:last-child") as HTMLTableCellElement;

  const editButton = document.createElement("button");
  editButton.className = "btn btn-sm btn-outline-secondary me-2";
  editButton.textContent = "Bearbeiten";
  editButton.addEventListener("click", () => openEditModal(user));

  const deleteButton = document.createElement("button");
  deleteButton.className = "btn btn-sm btn-outline-danger";
  deleteButton.textContent = "Löschen";
  deleteButton.addEventListener("click", () => onDelete(user));

  actionsCell.append(editButton, deleteButton);

  return row;
}

async function loadUsers(): Promise<void> {
  hideListError();
  try {
    const users = await fetchUsers(searchInput.value.trim() || undefined);
    tableBody.replaceChildren(...users.map(renderRow));
  } catch (error) {
    showListError(error instanceof Error ? error.message : "Benutzer konnten nicht geladen werden.");
  }
}

function openAddModal(): void {
  hideFormError();
  userModalTitle.textContent = "Benutzer hinzufügen";
  userIdInput.value = "";
  userUsernameInput.value = "";
  userRoleInput.value = "USER";
  userPasswordInput.value = "";
  userPasswordInput.required = true;
  userPasswordHint.textContent = "Mindestens 8 Zeichen.";
  userModal.show();
}

function openEditModal(user: ManagedUser): void {
  hideFormError();
  userModalTitle.textContent = "Benutzer bearbeiten";
  userIdInput.value = String(user.id);
  userUsernameInput.value = user.username;
  userRoleInput.value = user.role;
  userPasswordInput.value = "";
  userPasswordInput.required = false;
  userPasswordHint.textContent = "Leer lassen, um das Passwort nicht zu ändern.";
  userModal.show();
}

async function onDelete(user: ManagedUser): Promise<void> {
  const confirmed = window.confirm(`Benutzer "${user.username}" wirklich löschen?`);
  if (!confirmed) return;

  try {
    await deleteUser(user.id);
    await loadUsers();
  } catch (error) {
    showListError(error instanceof Error ? error.message : "Benutzer konnte nicht gelöscht werden.");
  }
}

userForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideFormError();

  const id = userIdInput.value;
  const username = userUsernameInput.value.trim();
  const role = userRoleInput.value;
  const password = userPasswordInput.value;

  try {
    if (id) {
      await updateUser(Number(id), username, role, password);
    } else {
      await createUser(username, password, role);
    }
    userModal.hide();
    await loadUsers();
  } catch (error) {
    showFormError(error instanceof Error ? error.message : "Speichern fehlgeschlagen.");
  }
});

addUserButton.addEventListener("click", openAddModal);

searchInput.addEventListener("input", () => {
  clearTimeout(searchDebounce);
  searchDebounce = setTimeout(loadUsers, 300);
});

void loadUsers();

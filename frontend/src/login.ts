import { login } from "./auth-api";
import { setToken, isLoggedIn } from "./auth-storage";

if (isLoggedIn()) {
  window.location.replace("/pages/dashboard.html");
}

const form = document.getElementById("loginForm") as HTMLFormElement;
const errorBox = document.getElementById("loginError") as HTMLDivElement;
const usernameInput = document.getElementById("username") as HTMLInputElement;
const passwordInput = document.getElementById("password") as HTMLInputElement;
const submitButton = form.querySelector("button[type=submit]") as HTMLButtonElement;

function showError(message: string): void {
  errorBox.innerHTML = `<strong>Anmeldung fehlgeschlagen</strong><br>${message}`;
  errorBox.classList.remove("d-none");
}

function hideError(): void {
  errorBox.classList.add("d-none");
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  hideError();

  submitButton.disabled = true;
  try {
    const token = await login(usernameInput.value, passwordInput.value);
    setToken(token);
    window.location.href = "/pages/dashboard.html";
  } catch {
    // The backend deliberately returns a generic 401 for both an unknown
    // username and a wrong password, so the UI text stays generic too.
    showError("Benutzername oder Passwort ist falsch.");
  } finally {
    submitButton.disabled = false;
  }
});
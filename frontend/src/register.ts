import { register } from "./auth-api";
import { isLoggedIn } from "./auth-storage";

if (isLoggedIn()) {
  window.location.replace("/pages/dashboard.html");
}

const form = document.getElementById("registerForm") as HTMLFormElement;
const errorBox = document.getElementById("registerError") as HTMLDivElement;
const successBox = document.getElementById("registerSuccess") as HTMLDivElement;
const usernameInput = document.getElementById("username") as HTMLInputElement;
const passwordInput = document.getElementById("password") as HTMLInputElement;
const submitButton = form.querySelector("button[type=submit]") as HTMLButtonElement;

function showError(message: string): void {
  successBox.classList.add("d-none");
  errorBox.textContent = message;
  errorBox.classList.remove("d-none");
}

function showSuccess(): void {
  errorBox.classList.add("d-none");
  successBox.textContent = "Konto erstellt. Du wirst zum Login weitergeleitet …";
  successBox.classList.remove("d-none");
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  submitButton.disabled = true;
  try {
    await register(usernameInput.value, passwordInput.value);
    showSuccess();
    setTimeout(() => window.location.href = "/pages/login.html", 1200);
  } catch (error) {
    showError(error instanceof Error ? error.message : "Registrierung fehlgeschlagen.");
    submitButton.disabled = false;
  }
});

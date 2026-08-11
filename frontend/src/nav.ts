import { clearToken, isAdmin, isLoggedIn } from "./auth-storage";
import { disconnectLobbySocket } from "./lobby-socket";

const adminNavItem = document.getElementById("adminNavItem");
const accountNavGroup = document.getElementById("accountNavGroup");
const logoutButton = document.getElementById("logoutButton");

if (isLoggedIn()) {
  accountNavGroup?.classList.remove("d-none");

  if (isAdmin()) {
    adminNavItem?.classList.remove("d-none");
  }
}

logoutButton?.addEventListener("click", () => {
  disconnectLobbySocket();
  clearToken();
  window.location.href = "/pages/login.html";
});

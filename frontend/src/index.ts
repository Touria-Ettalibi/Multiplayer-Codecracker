import { isLoggedIn } from "./auth-storage";

window.location.replace(isLoggedIn() ? "/pages/dashboard.html" : "/pages/login.html");
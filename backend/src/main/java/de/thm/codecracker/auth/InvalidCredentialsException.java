package de.thm.codecracker.auth;

/**
 * Indicates that a login attempt used an unknown username or a wrong password.
 *
 * <p>The message is intentionally generic so that it does not reveal whether
 * the username or the password was incorrect.</p>
 */
public class InvalidCredentialsException extends RuntimeException {
  public InvalidCredentialsException() {
    super("Invalid username or password");
  }
}

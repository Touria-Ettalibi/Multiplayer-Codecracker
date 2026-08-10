package de.thm.codecracker.auth;

/**
 * Indicates that a registration attempt used a username that is already taken.
 */
public class UsernameAlreadyExistsException extends RuntimeException {
  public UsernameAlreadyExistsException() {
    super("Username is already taken");
  }
}

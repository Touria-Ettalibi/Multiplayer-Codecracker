package de.thm.codecracker.user;

/**
 * Indicates that a requested User does not exist.
 */
public class UserNotFoundException extends RuntimeException {
  public UserNotFoundException() {
    super("User not found");
  }
}

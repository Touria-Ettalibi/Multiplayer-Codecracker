package de.thm.codecracker.user;

/**
 * Indicates that an operation was rejected because it would leave the
 * application without any User in the {@code ADMIN} role.
 */
public class LastAdminException extends RuntimeException {
  public LastAdminException() {
    super("Cannot remove the last remaining admin account");
  }
}
package de.thm.codecracker.auth.model;

/**
 * Represents a User in the application domain.
 *
 * @param id           the unique user ID
 * @param username     the unique username
 * @param passwordHash the bcrypt hash of the user's password
 * @param role         the user's role, either {@code "ADMIN"} or {@code "USER"}
 * @param createdAt    the creation time as Unix epoch milliseconds
 */
public record User(
  Long id,
  String username,
  String passwordHash,
  String role,
  long createdAt
) {
}

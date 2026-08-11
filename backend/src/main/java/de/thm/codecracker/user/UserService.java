package de.thm.codecracker.user;

import java.util.List;
import java.util.Set;

import de.thm.codecracker.auth.UserRepository;
import de.thm.codecracker.auth.UsernameAlreadyExistsException;
import de.thm.codecracker.auth.model.User;
import io.vertx.core.Future;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Provides business logic for admin user management.
 *
 * <p>This is distinct from {@link de.thm.codecracker.auth.AuthService}:
 * {@code AuthService} handles the self-service register/login flow, while
 * this service handles an admin managing other Users' accounts (listing,
 * searching, creating, editing and deleting).</p>
 */
public class UserService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_USERNAME_LENGTH = 50;
  private static final Set<String> VALID_ROLES = Set.of("ADMIN", "USER");

  private final UserRepository userRepository;

  /**
   * Creates a new User service.
   *
   * @param userRepository the repository used for User persistence
   */
  public UserService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  /**
   * Returns all Users, optionally filtered by a username search term.
   *
   * @param search a substring to match against usernames, or {@code null} for all Users
   * @return a future containing the matching Users
   */
  public Future<List<User>> findAll(String search) {
    return userRepository.findAll(search);
  }

  /**
   * Finds a User by its ID.
   *
   * @param id the user ID
   * @return a future containing the User
   * @throws IllegalArgumentException if the ID is invalid
   * @throws UserNotFoundException    if the User does not exist
   */
  public Future<User> findById(Long id) {
    validateId(id);

    User user = userRepository.findById(id).await();

    if (user == null) {
      throw new UserNotFoundException();
    }

    return Future.succeededFuture(user);
  }

  /**
   * Creates a new User account with an explicitly chosen role.
   *
   * @param username the desired username
   * @param password the plaintext password
   * @param role     the role to assign ({@code "ADMIN"} or {@code "USER"})
   * @return a future containing the created User
   * @throws IllegalArgumentException       if any field is invalid
   * @throws UsernameAlreadyExistsException if the username is already taken
   */
  public Future<User> create(String username, String password, String role) {
    validateUsername(username);
    validatePassword(password);
    validateRole(role);

    boolean exists = userRepository.existsByUsername(username).await();

    if (exists) {
      throw new UsernameAlreadyExistsException();
    }

    String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
    User user = userRepository.create(username, passwordHash, role).await();

    return Future.succeededFuture(user);
  }

  /**
   * Updates an existing User's username and role, and optionally its password.
   *
   * <p>Rejects the change if it would demote or delete the last remaining admin.</p>
   *
   * @param id       the user ID
   * @param username the new username
   * @param role     the new role
   * @param password the new plaintext password, or {@code null}/blank to keep the current one
   * @return a future containing the updated User
   * @throws IllegalArgumentException       if any field is invalid
   * @throws UserNotFoundException          if the User does not exist
   * @throws UsernameAlreadyExistsException if another User already has this username
   * @throws LastAdminException             if this would remove the last admin
   */
  public Future<User> update(Long id, String username, String role, String password) {
    validateId(id);
    validateUsername(username);
    validateRole(role);

    User existing = userRepository.findById(id).await();

    if (existing == null) {
      throw new UserNotFoundException();
    }

    boolean usernameTaken = userRepository.existsByUsernameExcludingId(username, id).await();

    if (usernameTaken) {
      throw new UsernameAlreadyExistsException();
    }

    if (existing.role().equals("ADMIN") && !role.equals("ADMIN")) {
      ensureNotLastAdmin();
    }

    User updated = userRepository.update(id, username, role).await();

    if (password != null && !password.isBlank()) {
      validatePassword(password);
      String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
      userRepository.updatePasswordHash(id, passwordHash).await();
    }

    return Future.succeededFuture(updated);
  }

  /**
   * Deletes a User by its ID.
   *
   * <p>Rejects the deletion if the User is the last remaining admin.</p>
   *
   * @param id the user ID
   * @return a succeeded future when the User was deleted
   * @throws IllegalArgumentException if the ID is invalid
   * @throws UserNotFoundException    if the User does not exist
   * @throws LastAdminException       if this would remove the last admin
   */
  public Future<Void> deleteById(Long id) {
    validateId(id);

    User existing = userRepository.findById(id).await();

    if (existing == null) {
      throw new UserNotFoundException();
    }

    if (existing.role().equals("ADMIN")) {
      ensureNotLastAdmin();
    }

    boolean deleted = userRepository.deleteById(id).await();

    if (!deleted) {
      throw new UserNotFoundException();
    }

    return Future.succeededFuture();
  }

  /**
   * Ensures at least one other admin exists before an admin is demoted/deleted.
   *
   * @throws LastAdminException if only one admin currently exists
   */
  private void ensureNotLastAdmin() {
    long adminCount = userRepository.countByRole("ADMIN").await();

    if (adminCount <= 1) {
      throw new LastAdminException();
    }
  }

  /**
   * Validates a User ID.
   *
   * @param id the user ID
   * @throws IllegalArgumentException if the ID is {@code null} or not positive
   */
  private void validateId(Long id) {
    if (id == null || id <= 0) {
      throw new IllegalArgumentException("Invalid user id");
    }
  }

  /**
   * Validates a username.
   *
   * @param username the username
   * @throws IllegalArgumentException if the username is empty or too long
   */
  private void validateUsername(String username) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("Username must not be empty");
    }

    if (username.length() > MAX_USERNAME_LENGTH) {
      throw new IllegalArgumentException("Username must not exceed 50 characters");
    }
  }

  /**
   * Validates a password.
   *
   * @param password the plaintext password
   * @throws IllegalArgumentException if the password is too short
   */
  private void validatePassword(String password) {
    if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
      throw new IllegalArgumentException("Password must be at least 8 characters long");
    }
  }

  /**
   * Validates a role.
   *
   * @param role the role
   * @throws IllegalArgumentException if the role is not {@code "ADMIN"} or {@code "USER"}
   */
  private void validateRole(String role) {
    if (role == null || !VALID_ROLES.contains(role)) {
      throw new IllegalArgumentException("Role must be either ADMIN or USER");
    }
  }
}

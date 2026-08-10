package de.thm.codecracker.auth;

import de.thm.codecracker.auth.model.User;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Provides business logic for registration and login.
 *
 * <p>The service validates input, hashes and verifies passwords with bcrypt,
 * coordinates database access through the {@link UserRepository}, and issues
 * signed JWTs on successful login.</p>
 */
public class AuthService {
  private static final int MIN_PASSWORD_LENGTH = 8;
  private static final int MAX_USERNAME_LENGTH = 50;
  private static final String DEFAULT_ROLE = "USER";
  private static final int TOKEN_EXPIRY_MINUTES = 8 * 60;

  private final UserRepository userRepository;
  private final JWTAuth jwtAuth;

  /**
   * Creates a new Auth service.
   *
   * @param userRepository the repository used for User persistence
   * @param jwtAuth        the Vert.x JWT provider used to sign tokens
   */
  public AuthService(UserRepository userRepository, JWTAuth jwtAuth) {
    this.userRepository = userRepository;
    this.jwtAuth = jwtAuth;
  }

  /**
   * Registers a new User with the default {@code USER} role.
   *
   * @param username the desired username
   * @param password the plaintext password
   * @return a future containing the created User
   * @throws IllegalArgumentException        if the username or password is invalid
   * @throws UsernameAlreadyExistsException  if the username is already taken
   */
  public Future<User> register(String username, String password) {
    validateUsername(username);
    validatePassword(password);

    boolean exists = userRepository.existsByUsername(username).await();

    if (exists) {
      throw new UsernameAlreadyExistsException();
    }

    String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
    User user = userRepository.create(username, passwordHash, DEFAULT_ROLE).await();

    return Future.succeededFuture(user);
  }

  /**
   * Verifies credentials and issues a signed JWT on success.
   *
   * @param username the username
   * @param password the plaintext password
   * @return a future containing the signed JWT
   * @throws InvalidCredentialsException if the username is unknown or the password is wrong
   */
  public Future<String> login(String username, String password) {
    if (username == null || username.isBlank() || password == null || password.isBlank()) {
      throw new InvalidCredentialsException();
    }

    User user = userRepository.findByUsername(username).await();

    if (user == null || !BCrypt.checkpw(password, user.passwordHash())) {
      throw new InvalidCredentialsException();
    }

    JsonObject claims = new JsonObject()
      .put("sub", user.username())
      .put("uid", user.id())
      .put("role", user.role());

    JWTOptions options = new JWTOptions()
      .setExpiresInMinutes(TOKEN_EXPIRY_MINUTES)
      .setIssuer("codecracker");

    String token = jwtAuth.generateToken(claims, options);

    return Future.succeededFuture(token);
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
}

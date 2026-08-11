package de.thm.codecracker.game;

/**
 * Indicates that a requested Game does not exist.
 */
public class GameNotFoundException extends RuntimeException {
  public GameNotFoundException() {
    super("Game not found");
  }
}

package de.thm.codecracker.game;

/**
 * Indicates that a submitted guess could not be accepted — the User is
 * not part of this Game, has already forfeited, has already submitted a
 * guess for the current round, or the Game/round is no longer active.
 */
public class InvalidGuessException extends RuntimeException {
  public InvalidGuessException(String message) {
    super(message);
  }
}

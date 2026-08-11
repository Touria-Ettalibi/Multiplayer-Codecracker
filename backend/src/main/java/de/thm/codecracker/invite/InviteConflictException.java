package de.thm.codecracker.invite;

/**
 * Indicates that starting a game was rejected because the lobby is not in
 * a state that allows it: an invite is already pending, a game is already
 * running, or too few Users are currently connected to ever reach the
 * two-ready-players minimum.
 */
public class InviteConflictException extends RuntimeException {
  public InviteConflictException(String message) {
    super(message);
  }
}

package de.thm.codecracker.invite;

/**
 * Indicates that a User tried to respond to the current invite (or there
 * was no active invite to respond to) despite not being one of the Users
 * invited when it started — e.g. they connected to the lobby afterward.
 */
public class NotInvitedException extends RuntimeException {
  public NotInvitedException(String message) {
    super(message);
  }
}

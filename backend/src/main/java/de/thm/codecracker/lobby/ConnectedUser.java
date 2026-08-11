package de.thm.codecracker.lobby;

/**
 * Represents a User currently connected to the lobby.
 *
 * @param id       the User's ID
 * @param username the User's username
 */
public record ConnectedUser(Long id, String username) {
}

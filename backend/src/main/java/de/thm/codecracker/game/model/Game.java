package de.thm.codecracker.game.model;

/**
 * Represents a Game, mirroring the {@code games} table.
 *
 * @param id                     the Game's ID
 * @param status                 {@code "WAITING"}, {@code "IN_PROGRESS"}, or {@code "FINISHED"}
 * @param secretCode             the secret code, e.g. {@code "RGBY"} (one letter per color)
 * @param maxRounds              the maximum number of rounds before the game ends undecided
 * @param roundTimeLimitSeconds  the time limit per round, in seconds
 * @param startedAt              when the Game moved to {@code IN_PROGRESS}, as Unix epoch
 *                                milliseconds, or {@code null} if not yet started
 * @param endedAt                when the Game moved to {@code FINISHED}, as Unix epoch
 *                                milliseconds, or {@code null} if not yet finished
 * @param createdAt              when the Game row was created, as Unix epoch milliseconds
 */
public record Game(
  Long id,
  String status,
  String secretCode,
  int maxRounds,
  int roundTimeLimitSeconds,
  Long startedAt,
  Long endedAt,
  long createdAt
) {
  public static final String STATUS_WAITING = "WAITING";
  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_FINISHED = "FINISHED";
}

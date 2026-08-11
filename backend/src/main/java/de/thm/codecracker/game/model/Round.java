package de.thm.codecracker.game.model;

/**
 * Represents one Round of a Game, mirroring the {@code rounds} table.
 *
 * @param id          the Round's ID
 * @param gameId      the Game this Round belongs to
 * @param roundNumber 1-based round number within the Game
 * @param startedAt   when this Round started, as Unix epoch milliseconds — used as
 *                     the shared countdown reference point so every client's
 *                     "Verbleibende Zeit" agrees, instead of each browser starting
 *                     its own local timer at a slightly different moment
 * @param endedAt     when this Round ended, as Unix epoch milliseconds, or
 *                     {@code null} while still active
 */
public record Round(
  Long id,
  Long gameId,
  int roundNumber,
  long startedAt,
  Long endedAt
) {
}

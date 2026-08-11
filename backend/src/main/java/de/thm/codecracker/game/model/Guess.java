package de.thm.codecracker.game.model;

/**
 * Represents one User's guess in one Round, mirroring the {@code guesses} table.
 *
 * @param id                    the Guess's ID
 * @param roundId               the Round this Guess was submitted in
 * @param userId                the guessing User's ID
 * @param guessedCode           the submitted code, or {@code null} if nothing was submitted at all
 * @param correctPositionCount  how many colors are correct <em>and</em> in the correct position,
 *                              or {@code null} if the guess was invalid
 * @param correctColorCount     how many further colors are correct but in the wrong position,
 *                              or {@code null} if the guess was invalid
 * @param isValid               {@code false} if the code was incomplete/malformed and therefore
 *                              does not count as a real attempt
 * @param submittedAt           when this Guess was recorded, as Unix epoch milliseconds
 */
public record Guess(
  Long id,
  Long roundId,
  Long userId,
  String guessedCode,
  Integer correctPositionCount,
  Integer correctColorCount,
  boolean isValid,
  long submittedAt
) {
}

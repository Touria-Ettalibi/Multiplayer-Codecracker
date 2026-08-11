package de.thm.codecracker.game.model;

/**
 * Represents one User's participation in a Game, mirroring the
 * {@code game_players} table.
 *
 * <p>There is no separate "active" column: a forfeiting player is simply
 * given the result {@code LOSS} immediately (per the README's rule that
 * forfeiting counts as a loss) — the same result they'd eventually get
 * anyway if they lost normally. {@link #isActive()} derives "still
 * playing" from {@code result == PENDING}, so no schema change is
 * needed just to track forfeits.</p>
 *
 * @param id        the row's ID
 * @param gameId    the Game this participation belongs to
 * @param userId    the participating User's ID
 * @param username  the participating User's username (joined in for convenience)
 * @param ready     whether the User confirmed readiness during the invite phase
 * @param result    {@code "WIN"}, {@code "DRAW"}, {@code "LOSS"}, or {@code "PENDING"}
 * @param points    points earned from this Game (3/1/-1/0, floored at 0 across a User's total)
 * @param joinedAt  when the User joined this Game, as Unix epoch milliseconds
 */
public record GamePlayer(
  Long id,
  Long gameId,
  Long userId,
  String username,
  boolean ready,
  String result,
  int points,
  long joinedAt
) {
  public static final String RESULT_WIN = "WIN";
  public static final String RESULT_DRAW = "DRAW";
  public static final String RESULT_LOSS = "LOSS";
  public static final String RESULT_PENDING = "PENDING";

  /**
   * A player is still actively playing as long as no result has been
   * decided for them yet — neither a normal round outcome nor an early
   * forfeit (which is recorded as an immediate {@code LOSS}).
   *
   * @return {@code true} if this player can still submit guesses
   */
  public boolean isActive() {
    return RESULT_PENDING.equals(result);
  }
}

package de.thm.codecracker.game;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes Mastermind-style feedback for one guess against the secret code.
 *
 * <p>Deliberately a standalone, pure function with no dependency on
 * persistence, sockets, or any other part of the game — the counting rule
 * itself (especially with repeated colors) is the single most bug-prone
 * part of this feature, so it is isolated here where it can be reasoned
 * about — and tested — completely on its own.</p>
 *
 * <h2>Why a naive per-position comparison is not enough</h2>
 * <p>Consider secret {@code "AABB"} against guess {@code "AAAA"}. A naive
 * approach might say "the guess has four A's, and the secret contains two
 * A's, so there are some additional wrong-position matches beyond the two
 * exact ones." That is wrong: once the two exact A-matches (positions 0
 * and 1) are accounted for, there are <strong>no A's left in the secret</strong>
 * to match against the guess's remaining A's at positions 2 and 3. The
 * correct result is 2 exact matches and 0 wrong-position matches — not 2
 * and 2.</p>
 *
 * <p>This class avoids that trap by removing exact-match positions first,
 * then capping the wrong-position count per color at
 * {@code min(remaining secret count of that color, remaining guess count
 * of that color)} — the standard approach for this problem.</p>
 *
 * <h2>Worked examples</h2>
 * <pre>
 *   secret=RRGB guess=RRRB -> exact=3, wrongPosition=0
 *     (the third R in the guess has nothing left in the secret to match)
 *
 *   secret=AABB guess=AAAA -> exact=2, wrongPosition=0
 *     (the classic duplicate-color trap described above)
 *
 *   secret=RGBY guess=GRBY -> exact=2, wrongPosition=2
 *     (R and G are simply swapped)
 * </pre>
 */
public final class GuessEvaluator {

  private GuessEvaluator() {
  }

  /**
   * Computes feedback for one guess against the secret code.
   *
   * @param secretCode the secret code, e.g. {@code "RGBY"}
   * @param guessCode  the guessed code, same length as {@code secretCode}
   * @return the resulting feedback
   * @throws IllegalArgumentException if the two codes differ in length
   */
  public static Feedback evaluate(String secretCode, String guessCode) {
    if (secretCode.length() != guessCode.length()) {
      throw new IllegalArgumentException("Secret code and guess code must have the same length");
    }

    int length = secretCode.length();
    int correctPosition = 0;

    Map<Character, Integer> remainingSecretColors = new HashMap<>();
    Map<Character, Integer> remainingGuessColors = new HashMap<>();

    for (int i = 0; i < length; i++) {
      char secretColor = secretCode.charAt(i);
      char guessColor = guessCode.charAt(i);

      if (secretColor == guessColor) {
        correctPosition++;
      } else {
        remainingSecretColors.merge(secretColor, 1, Integer::sum);
        remainingGuessColors.merge(guessColor, 1, Integer::sum);
      }
    }

    int correctColor = 0;
    for (Map.Entry<Character, Integer> entry : remainingGuessColors.entrySet()) {
      int availableInSecret = remainingSecretColors.getOrDefault(entry.getKey(), 0);
      correctColor += Math.min(availableInSecret, entry.getValue());
    }

    return new Feedback(correctPosition, correctColor);
  }

  /**
   * The result of comparing one guess to the secret code.
   *
   * @param correctPosition colors that are correct and in the correct position
   * @param correctColor    further colors that are correct but in the wrong position
   */
  public record Feedback(int correctPosition, int correctColor) {
    /**
     * @return {@code true} if every position matched, i.e. the guess is the secret code
     */
    public boolean isFullyCorrect(int codeLength) {
      return correctPosition == codeLength;
    }
  }
}

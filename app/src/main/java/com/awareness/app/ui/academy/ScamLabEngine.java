package com.awareness.app.ui.academy;

import com.awareness.app.model.ScamScenario;
import com.awareness.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Game rules for Scam Lab.
 *
 *  - Every WORD of the message is a choice the player can tap.
 *  - Tapping a word inside a red flag finds that flag.
 *  - Tapping any other word is a MISTAKE. Two different wrong words end the game.
 *  - The game also ends when every red flag has been found.
 *  - Either way the score is finalized (see ScamLabFragment.submitGame).
 */
public class ScamLabEngine {

    /** Wrong taps allowed before the game ends: the 2nd wrong word is the last. */
    public static final int MAX_MISTAKES = 2;

    // Codes returned by onSegmentTapped() (a value >= 0 is the index of the flag that was found)
    public static final int RESULT_WRONG         = -1;  // new mistake
    public static final int RESULT_ALREADY_FOUND = -2;  // a flag that was already found
    public static final int RESULT_ALREADY_WRONG = -3;  // a wrong word that was already tried (no 2nd penalty)
    public static final int RESULT_GAME_OVER     = -4;  // the game has already ended

    private final ScamScenario scenario;

    // Indices of red flags the user has successfully tapped
    private final Set<Integer> foundFlagIndices = new HashSet<>();

    // Wrong words already tapped, as "start:end", so tapping the same word twice isn't a 2nd mistake
    private final Set<String> wrongSegments = new HashSet<>();
    private int mistakes = 0;

    // How many hints the user has used (max 1 per game)
    private int hintsUsed = 0;
    private static final int MAX_HINTS = 1;

    // ── Constructor ──────────────────────────────────────────────
    public ScamLabEngine(ScamScenario scenario) {
        this.scenario = scenario;
        resolveFlagPositions(scenario);
    }

    /**
     * A red flag may give its exact words in `text`. Find them in the message so nobody has to count
     * characters. (Counting by hand is how the original demo ended up a few characters off.)
     */
    private static void resolveFlagPositions(ScamScenario scenario) {
        String message = scenario.getMessage();
        List<ScamScenario.RedFlag> flags = scenario.getRedFlags();
        if (message == null || flags == null) return;

        for (ScamScenario.RedFlag flag : flags) {
            String text = flag.getText();
            if (text == null || text.isEmpty()) continue;
            int at = message.indexOf(text);
            if (at >= 0) {
                flag.setStart(at);
                flag.setEnd(at + text.length());
            }
        }
    }

    // ── Core Game Actions ────────────────────────────────────────

    /**
     * Called when the player taps one word of the message (characters start..end).
     *
     * A word counts as part of a flag when MOST of it lies inside the flag's range, so a stray
     * punctuation mark at the edge of a flag can't decide the outcome.
     *
     * Returns the flag index (>= 0) when a NEW flag was found, otherwise one of the RESULT_* codes.
     */
    public int onSegmentTapped(int start, int end) {
        if (isGameOver()) return RESULT_GAME_OVER;

        int length = Math.max(1, end - start);
        List<ScamScenario.RedFlag> flags = scenario.getRedFlags();
        if (flags != null) {
            for (int i = 0; i < flags.size(); i++) {
                ScamScenario.RedFlag flag = flags.get(i);
                int overlap = Math.min(end, flag.getEnd()) - Math.max(start, flag.getStart());
                if (overlap > 0 && overlap * 2 >= length) {
                    if (foundFlagIndices.contains(i)) return RESULT_ALREADY_FOUND;
                    foundFlagIndices.add(i);
                    return i;
                }
            }
        }

        // Not part of any red flag
        if (!wrongSegments.add(start + ":" + end)) return RESULT_ALREADY_WRONG;
        mistakes++;
        return RESULT_WRONG;
    }

    /**
     * Activates a hint — reveals the next unfound flag.
     * Returns the RedFlag to highlight, or null if no hints left.
     */
    public ScamScenario.RedFlag useHint() {
        if (hintsUsed >= MAX_HINTS) return null;

        List<ScamScenario.RedFlag> flags = scenario.getRedFlags();
        for (int i = 0; i < flags.size(); i++) {
            if (!foundFlagIndices.contains(i)) {
                hintsUsed++;
                return flags.get(i); // Return the first unfound flag
            }
        }
        return null; // All found already
    }

    // ── Score Calculation ────────────────────────────────────────

    /**
     * Calculates total XP earned based on:
     *  - Base XP from the scenario
     *  - Bonus XP per flag found (Constants.XP_PER_RED_FLAG)
     *  - Penalty: -5 XP per hint used
     * (Mistakes don't cost XP: they end the game, which already limits how many flags you can find.)
     */
    public int calculateXpEarned() {
        int base    = scenario.getXpReward();
        int bonus   = foundFlagIndices.size() * Constants.XP_PER_RED_FLAG;
        int penalty = hintsUsed * 5;
        return Math.max(0, base + bonus - penalty);
    }

    /**
     * Returns the score as a percentage: flags found / total flags
     * Used to decide which result message to show.
     */
    public float getScorePercent() {
        int total = getTotalFlags();
        if (total == 0) return 100f;
        return (foundFlagIndices.size() / (float) total) * 100f;
    }

    // ── Result Data ──────────────────────────────────────────────

    /**
     * Returns a list of all flags with a "wasFound" boolean.
     * Used by the result screen to show explanations for each flag.
     */
    public List<FlagResult> getFlagResults() {
        List<FlagResult> results = new ArrayList<>();
        List<ScamScenario.RedFlag> flags = scenario.getRedFlags();
        for (int i = 0; i < flags.size(); i++) {
            results.add(new FlagResult(flags.get(i), foundFlagIndices.contains(i)));
        }
        return results;
    }

    /**
     * Returns a result title based on score.
     *  100%  → "Perfect! You're a Guardian! 🛡️"
     *  ≥ 50% → "Good Effort! Keep Practicing 💪"
     *  < 50% → "Stay Alert! Review the Red Flags ⚠️"
     */
    public String getResultTitle() {
        float score = getScorePercent();
        if (score == 100f) return "Perfect! You're a Guardian! 🛡️";
        if (score >= 50f)  return "Good Effort! Keep Practicing 💪";
        return "Stay Alert! Review the Red Flags ⚠️";
    }

    /** One line saying why the game ended (empty if the player pressed Submit themselves). */
    public String getEndReason() {
        if (allFlagsFound())    return "You found every red flag!";
        if (endedByMistakes())  return "Game ended after " + MAX_MISTAKES + " wrong taps.";
        return "";
    }

    // ── State Queries ────────────────────────────────────────────

    public int getFoundCount()   { return foundFlagIndices.size(); }
    public int getTotalFlags()   { return scenario.getRedFlags() != null
            ? scenario.getRedFlags().size() : 0; }
    public boolean allFlagsFound() { return getTotalFlags() > 0
            && foundFlagIndices.size() == getTotalFlags(); }

    public int     getMistakes()   { return mistakes; }
    public int     getLivesLeft()  { return Math.max(0, MAX_MISTAKES - mistakes); }
    public boolean endedByMistakes() { return mistakes >= MAX_MISTAKES && !allFlagsFound(); }

    /** True once every flag is found OR the player has used up their mistakes. */
    public boolean isGameOver()    { return allFlagsFound() || mistakes >= MAX_MISTAKES; }

    public boolean canUseHint()    { return hintsUsed < MAX_HINTS; }
    public ScamScenario getScenario() { return scenario; }

    // ── Inner Result Class ───────────────────────────────────────

    /**
     * Wraps a RedFlag with a "wasFound" boolean for the result screen.
     */
    public static class FlagResult {
        public final ScamScenario.RedFlag flag;
        public final boolean wasFound;

        FlagResult(ScamScenario.RedFlag flag, boolean wasFound) {
            this.flag     = flag;
            this.wasFound = wasFound;
        }
    }
}

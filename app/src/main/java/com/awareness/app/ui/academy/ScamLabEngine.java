package com.awareness.app.ui.academy;

import com.awareness.app.model.ScamScenario;
import com.awareness.app.utils.Constants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ScamLabEngine {

    private final ScamScenario scenario;

    // Indices of red flags the user has successfully tapped
    private final Set<Integer> foundFlagIndices = new HashSet<>();

    // How many hints the user has used (max 1 per game)
    private int hintsUsed = 0;
    private static final int MAX_HINTS = 1;

    // ── Constructor ──────────────────────────────────────────────
    public ScamLabEngine(ScamScenario scenario) {
        this.scenario = scenario;
    }

    // ── Core Game Actions ────────────────────────────────────────

    /**
     * Called when the user taps a position in the message text.
     * Checks if any red flag contains that character position.
     *
     * Returns the index of the flag that was hit, or -1 if nothing there.
     * The Fragment uses this to highlight the tapped flag.
     */
    public int onTextTapped(int characterPosition) {
        List<ScamScenario.RedFlag> flags = scenario.getRedFlags();
        for (int i = 0; i < flags.size(); i++) {
            ScamScenario.RedFlag flag = flags.get(i);
            if (characterPosition >= flag.getStart() && characterPosition < flag.getEnd()) {
                if (!foundFlagIndices.contains(i)) {
                    // New flag found!
                    foundFlagIndices.add(i);
                    return i;
                } else {
                    // Already found — return special code
                    return -2;
                }
            }
        }
        // Tapped empty space — no flag there
        return -1;
    }

    /**
     * Activates a hint — reveals the START index of the next unfound flag.
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

    // ── State Queries ────────────────────────────────────────────

    public int getFoundCount()   { return foundFlagIndices.size(); }
    public int getTotalFlags()   { return scenario.getRedFlags() != null
            ? scenario.getRedFlags().size() : 0; }
    public boolean allFlagsFound() { return foundFlagIndices.size() == getTotalFlags(); }
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

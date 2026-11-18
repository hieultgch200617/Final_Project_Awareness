package com.awareness.app.ui.academy;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.awareness.app.R;
import com.awareness.app.databinding.FragmentScamLabBinding;
import com.awareness.app.model.ScamScenario;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScamLabFragment extends Fragment {

    private FragmentScamLabBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;

    private ScamLabEngine engine;

    // The SpannableString that makes text tappable
    private SpannableString spannableMessage;

    // inputLocked: the game is over, ignore further taps. submitted: the score was already saved.
    private boolean inputLocked = false;
    private boolean submitted   = false;

    // ── Lifecycle ─────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentScamLabBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db            = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        loadRandomScenario();
    }

    // ── Data Loading ──────────────────────────────────────────────

    /**
     * Fetches all published scenarios from Firestore,
     * then picks one at random to display.
     *
     * We load all (max ~20 scenarios) because Firestore doesn't
     * support random document selection natively. With a small
     * collection this is fine and cheap.
     */
    private void loadRandomScenario() {
        showState(GameState.LOADING);

        db.collection("scam_scenarios")
                .whereEqualTo("published", true)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<ScamScenario> scenarios = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        ScamScenario s = doc.toObject(ScamScenario.class);
                        scenarios.add(s);
                    }

                    if (scenarios.isEmpty()) {
                        // No scenarios in Firestore yet — load the hardcoded demo
                        startGame(buildDemoScenario());
                    } else {
                        // Pick a random scenario
                        ScamScenario picked = scenarios.get(
                                new Random().nextInt(scenarios.size()));
                        startGame(picked);
                    }
                })
                .addOnFailureListener(e -> {
                    // Network error — fall back to demo scenario
                    startGame(buildDemoScenario());
                });
    }

    // ── Game Start ────────────────────────────────────────────────

    /**
     * Initializes the game engine with a scenario and renders the UI.
     */
    private void startGame(ScamScenario scenario) {
        engine = new ScamLabEngine(scenario);
        inputLocked = false;
        submitted   = false;
        binding.btnHint.setEnabled(true);      // were left disabled after a previous round
        binding.btnHint.setAlpha(1f);
        binding.btnSubmit.setEnabled(true);
        binding.tvTapFeedback.setVisibility(View.INVISIBLE);

        renderScenarioHeader(scenario);
        renderMessageWithTappableSpans(scenario);
        updateFlagCounter();
        updateLives();
        setupGameButtons();

        showState(GameState.PLAYING);
    }

    /**
     * Fills in the sender header (avatar letter, name, type badge).
     */
    private void renderScenarioHeader(ScamScenario scenario) {
        String sender = scenario.getSenderLabel() != null
                ? scenario.getSenderLabel() : "Unknown";

        // First letter of sender for the avatar circle
        binding.tvSenderAvatar.setText(sender.substring(0, 1).toUpperCase());
        binding.tvSenderName.setText(sender);
        binding.tvScenarioCategory.setText(scenario.getCategory());

        // Format the type label nicely
        String typeLabel;
        switch (scenario.getScenarioType() != null ? scenario.getScenarioType() : "") {
            case "email":   typeLabel = "📧 Email"; break;
            case "webpage": typeLabel = "🌐 Webpage"; break;
            default:        typeLabel = "📱 SMS Message";
        }
        binding.tvScenarioType.setText(typeLabel);
    }

    /**
     * The most important rendering function.
     *
     * EVERY WORD of the message becomes its own tappable span (WordSpan), so any part of the message
     * can be chosen. Red-flag words are NOT underlined or marked in any way, because that would give
     * the answers away.
     *
     * Why ClickableSpan + LinkMovementMethod: the previous version used a custom OnTouchListener that
     * only reacted to ACTION_UP but never accepted ACTION_DOWN. Android only delivers the finger-lift
     * to a view that accepted the touch-down, so taps were silently ignored. LinkMovementMethod
     * handles the whole touch sequence (and works inside a ScrollView).
     */
    private void renderMessageWithTappableSpans(ScamScenario scenario) {
        String message = scenario.getMessage() != null ? scenario.getMessage() : "";
        spannableMessage = new SpannableString(message);

        Matcher word = Pattern.compile("\\S+").matcher(message);   // a "word" = anything between spaces
        while (word.find()) {
            spannableMessage.setSpan(
                    new WordSpan(word.start(), word.end()),
                    word.start(), word.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        binding.tvMessage.setText(spannableMessage);
        binding.tvMessage.setMovementMethod(LinkMovementMethod.getInstance());

        // The soft blue flash while a word is pressed
        int blue = ContextCompat.getColor(requireContext(), R.color.brand_blue);
        binding.tvMessage.setHighlightColor((blue & 0x00FFFFFF) | 0x40000000);
    }

    /** One tappable word. Keeps the normal text colour (no link blue, no underline). */
    private class WordSpan extends ClickableSpan {
        private final int start;
        private final int end;

        WordSpan(int start, int end) {
            this.start = start;
            this.end   = end;
        }

        @Override
        public void onClick(@NonNull View widget) {
            handleSegmentTap(start, end);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setUnderlineText(false);   // don't change colour: the message must look like plain text
        }
    }

    /**
     * Called after every tap on a word. Asks the engine what the tap was, then updates the screen.
     * The game ends (and the score is finalized) when all flags are found OR on the 2nd wrong word.
     */
    private void handleSegmentTap(int start, int end) {
        if (inputLocked || binding == null) return;

        int result = engine.onSegmentTapped(start, end);

        if (result >= 0) {
            // ✅ New red flag found!
            ScamScenario.RedFlag flag = engine.getScenario().getRedFlags().get(result);
            highlightFoundFlag(flag, result);
            updateFlagCounter();

            if (engine.allFlagsFound()) {
                showTapFeedback("🎉 You found every red flag!", true);
                finishGameSoon();
            } else {
                showTapFeedback("🚩 Red Flag Found: " + flag.getLabel(), true);
            }

        } else if (result == ScamLabEngine.RESULT_WRONG) {
            // ❌ Not a red flag: cross the word out and use up a life
            markWrongWord(start, end);
            updateLives();

            if (engine.getLivesLeft() == 0) {
                showTapFeedback("❌ Second wrong tap. Game over!", false);
                finishGameSoon();
            } else {
                showTapFeedback("❌ Not a red flag. " + engine.getLivesLeft()
                        + (engine.getLivesLeft() == 1 ? " life left!" : " lives left!"), false);
            }

        } else if (result == ScamLabEngine.RESULT_ALREADY_FOUND) {
            showTapFeedback("Already flagged!", false);

        } else if (result == ScamLabEngine.RESULT_ALREADY_WRONG) {
            showTapFeedback("You already tried that word.", false);
        }
    }

    /** Locks the message, shows the last feedback for a moment, then finalizes the score. */
    private void finishGameSoon() {
        inputLocked = true;
        binding.btnHint.setEnabled(false);
        binding.btnSubmit.setEnabled(false);
        new Handler(Looper.getMainLooper()).postDelayed(this::submitGame, 1200);
    }

    /** Strikes through a wrongly tapped word, on a faint red background. */
    private void markWrongWord(int start, int end) {
        int red = ContextCompat.getColor(requireContext(), R.color.error_red);
        spannableMessage.setSpan(new BackgroundColorSpan((red & 0x00FFFFFF) | 0x33000000),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableMessage.setSpan(new StrikethroughSpan(),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        spannableMessage.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.text_secondary)),
                start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        binding.tvMessage.setText(spannableMessage);
    }

    /**
     * Visually highlights a found red flag in the message text.
     * Changes the background to red and text to white.
     */
    private void highlightFoundFlag(ScamScenario.RedFlag flag, int flagIndex) {
        // Red background highlight
        spannableMessage.setSpan(
                new BackgroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.error_red)),
                flag.getStart(), flag.getEnd(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        // White text on red background
        spannableMessage.setSpan(
                new ForegroundColorSpan(Color.WHITE),
                flag.getStart(), flag.getEnd(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        binding.tvMessage.setText(spannableMessage);
    }

    /**
     * Highlights a hinted flag with a yellow/amber background (softer than red).
     */
    private void highlightHintedFlag(ScamScenario.RedFlag flag) {
        spannableMessage.setSpan(
                new BackgroundColorSpan(
                        ContextCompat.getColor(requireContext(), R.color.xp_gold)),
                flag.getStart(), flag.getEnd(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        binding.tvMessage.setText(spannableMessage);
    }

    // ── Button Setup ──────────────────────────────────────────────

    private void setupGameButtons() {
        // Hint button
        binding.btnHint.setOnClickListener(v -> {
            if (!engine.canUseHint()) {
                showTapFeedback("No hints left!", false);
                return;
            }
            ScamScenario.RedFlag hinted = engine.useHint();
            if (hinted != null) {
                highlightHintedFlag(hinted);
                showTapFeedback("💡 Hint: Look at the highlighted area!", false);
                binding.btnHint.setEnabled(false);
                binding.btnHint.setAlpha(0.5f);
            }
        });

        // Submit button
        binding.btnSubmit.setOnClickListener(v -> submitGame());

        // Play Again (in result state)
        binding.btnPlayAgain.setOnClickListener(v -> {
            showState(GameState.LOADING);
            loadRandomScenario();
        });

        // Back to Academy (in result state)
        binding.btnBackToAcademy.setOnClickListener(v ->
                Navigation.findNavController(requireView()).navigateUp());
    }

    // ── Game Submission ───────────────────────────────────────────

    /**
     * Ends the game, calculates the score, saves to Firestore,
     * and transitions to the result state.
     */
    private void submitGame() {
        if (binding == null || submitted) return;   // screen closed, or already finalized
        submitted   = true;
        inputLocked = true;

        int xpEarned = engine.calculateXpEarned();
        saveScoreToFirestore(xpEarned);
        showResultScreen(xpEarned);
    }

    /**
     * Saves the Scam Lab score to Firestore.
     * Adds XP to the user and logs the play in a sub-collection.
     */
    private void saveScoreToFirestore(int xpEarned) {
        if (xpEarned <= 0) return;

        com.google.firebase.firestore.DocumentReference userRef =
                db.collection(Constants.COLLECTION_USERS).document(currentUserId);

        // Add XP
        userRef.update("xpPoints", FieldValue.increment(xpEarned));

        // Log the scam lab play
        userRef.collection("scamLabPlays")
                .add(new java.util.HashMap<String, Object>() {{
                    put("scenarioId", engine.getScenario().getScenarioId());
                    put("flagsFound", engine.getFoundCount());
                    put("totalFlags", engine.getTotalFlags());
                    put("mistakes",   engine.getMistakes());
                    put("xpEarned",   xpEarned);
                    put("playedAt",   FieldValue.serverTimestamp());
                }});
    }

    // ── Result Screen ─────────────────────────────────────────────

    /**
     * Populates and shows the result screen with:
     * - Result title (based on score %)
     * - Score: X/Y flags
     * - XP earned
     * - One explanation card per red flag
     */
    private void showResultScreen(int xpEarned) {
        // Fill result header
        binding.tvResultTitle.setText(engine.getResultTitle());
        String endReason = engine.getEndReason();
        binding.tvResultScore.setText(
                engine.getFoundCount() + " / " + engine.getTotalFlags() + " Red Flags Found"
                        + (endReason.isEmpty() ? "" : "\n" + endReason));
        binding.tvResultXp.setText("+" + xpEarned + " XP Earned");

        // Play Lottie animation
        binding.lottieResult.playAnimation();

        // Build one explanation card per flag
        binding.layoutFlagBreakdown.removeAllViews();
        for (ScamLabEngine.FlagResult flagResult : engine.getFlagResults()) {
            addFlagResultCard(flagResult);
        }

        showState(GameState.RESULT);
    }

    /**
     * Inflates one item_flag_result.xml card and adds it to layoutFlagBreakdown.
     * Green check = found, red X = missed.
     */
    private void addFlagResultCard(ScamLabEngine.FlagResult flagResult) {
        View cardView = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_flag_result, binding.layoutFlagBreakdown, false);

        ImageView imgStatus       = cardView.findViewById(R.id.imgFlagStatus);
        TextView  tvLabel         = cardView.findViewById(R.id.tvFlagLabel);
        TextView  tvExplanation   = cardView.findViewById(R.id.tvFlagExplanation);

        tvLabel.setText(flagResult.flag.getLabel());
        tvExplanation.setText(flagResult.flag.getExplanation());

        if (flagResult.wasFound) {
            imgStatus.setImageResource(R.drawable.ic_check_circle);
            imgStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.guardian_green));
            tvLabel.setTextColor(
                    ContextCompat.getColor(requireContext(), R.color.guardian_green));
        } else {
            imgStatus.setImageResource(R.drawable.ic_cancel);
            imgStatus.setColorFilter(
                    ContextCompat.getColor(requireContext(), R.color.error_red));
        }

        binding.layoutFlagBreakdown.addView(cardView);
    }

    // ── UI Helpers ────────────────────────────────────────────────

    private void updateFlagCounter() {
        int found = engine.getFoundCount();
        int total = engine.getTotalFlags();
        binding.tvFlagCount.setText("🚩 Red Flags: " + found + " / " + total);

        // Update progress bar
        int progress = total > 0 ? (found * 100) / total : 0;
        binding.progressFlags.setProgress(progress, true);
    }

    /** ❤️❤️ → ❤️🖤 → 🖤🖤 : lives left (a wrong word costs one). */
    private void updateLives() {
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < ScamLabEngine.MAX_MISTAKES; i++) {
            hearts.append(i < engine.getLivesLeft() ? "❤️" : "🖤");
        }
        binding.tvLives.setText(hearts.toString());
    }

    /**
     * Shows a brief feedback message after each tap.
     * Fades out automatically after 1.5 seconds.
     */
    private void showTapFeedback(String message, boolean isSuccess) {
        binding.tvTapFeedback.setText(message);
        binding.tvTapFeedback.setTextColor(ContextCompat.getColor(requireContext(),
                isSuccess ? R.color.guardian_green : R.color.text_secondary));
        binding.tvTapFeedback.setVisibility(View.VISIBLE);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (binding != null) binding.tvTapFeedback.setVisibility(View.INVISIBLE);
        }, 1500);
    }

    // Game screen states
    private enum GameState { LOADING, PLAYING, RESULT }

    private void showState(GameState state) {
        binding.progressBar.setVisibility(
                state == GameState.LOADING ? View.VISIBLE : View.GONE);
        binding.layoutGame.setVisibility(
                state == GameState.PLAYING ? View.VISIBLE : View.GONE);
        binding.layoutResult.setVisibility(
                state == GameState.RESULT ? View.VISIBLE : View.GONE);
    }

    // ── Demo Scenario (used when Firestore is empty) ──────────────

    /**
     * A hardcoded demo scenario used as a fallback when Firestore has no published scenarios.
     *
     * Red flags are given by their exact words, and the game locates them in the message itself
     * (see ScamLabEngine.resolveFlagPositions). The original version used hand-counted character
     * numbers that were off by 6-21 characters, so the real URL / OTP words weren't inside the flags.
     *
     * To add scenarios to Firestore (collection "scam_scenarios", published = true), copy this shape:
     *   message, title, category, scenarioType, senderLabel, xpReward, and redFlags: an array of
     *   {text: "exact words", label: "...", explanation: "..."}
     */
    private ScamScenario buildDemoScenario() {
        ScamScenario s = new ScamScenario();
        s.setTitle("Suspicious Bank SMS");
        s.setCategory("Phishing");
        s.setScenarioType("sms");
        s.setSenderLabel("Vietcombank");
        s.setXpReward(Constants.XP_PER_SCAM_LAB);

        String msg =
                "Tài khoản VCB của bạn đã bị khóa. " +
                        "Truy cập http://vcb-secure.tk/verify " +
                        "để xác minh ngay. " +
                        "Nhập mã OTP: 482910 để xác nhận. " +
                        "Hỗ trợ: 1900.fake.vietcombank.com";
        s.setMessage(msg);

        List<ScamScenario.RedFlag> flags = new ArrayList<>();

        flags.add(makeFlag("http://vcb-secure.tk/verify", "Fake Domain",
                "\"vcb-secure.tk\" is NOT a real Vietcombank domain. " +
                        "The real domain is vietcombank.com.vn. " +
                        ".tk domains are free and commonly used by scammers."));

        flags.add(makeFlag("Nhập mã OTP: 482910", "OTP in Message",
                "Real banks NEVER send your OTP inside an SMS. " +
                        "An OTP is a one-time password YOU generate or receive to log in — " +
                        "any message containing an OTP is asking you to confirm a transaction " +
                        "you didn't make."));

        flags.add(makeFlag("1900.fake.vietcombank.com", "Fake Support URL",
                "The support link contains \"fake\" in the domain and is not " +
                        "an official Vietcombank address. Scammers insert bank names " +
                        "into fake URLs to look legitimate."));

        s.setRedFlags(flags);
        return s;
    }

    /** A red flag identified by its exact words; the engine finds its position in the message. */
    private ScamScenario.RedFlag makeFlag(String exactText, String label, String explanation) {
        ScamScenario.RedFlag flag = new ScamScenario.RedFlag();
        flag.setText(exactText);
        flag.setLabel(label);
        flag.setExplanation(explanation);
        return flag;
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

package com.awareness.app.ui.academy;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
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

public class ScamLabFragment extends Fragment {

    private FragmentScamLabBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;

    private ScamLabEngine engine;

    // The SpannableString that makes text tappable
    private SpannableString spannableMessage;

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

        renderScenarioHeader(scenario);
        renderMessageWithTappableSpans(scenario);
        updateFlagCounter();
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
     * Takes the plain message text and converts it into a SpannableString
     * where every character is tappable. We use a custom TouchListener
     * (not ClickableSpan) because ClickableSpan requires
     * android:autoLink which conflicts with our custom spans.
     *
     * How it works:
     * 1. Build a SpannableString from the message text
     * 2. Apply a subtle underline to each red flag zone so players
     *    can see "something is here" (a soft hint by design)
     * 3. Set a custom OnTouchListener that maps the tap X/Y
     *    coordinates → character offset → engine.onTextTapped()
     */
    private void renderMessageWithTappableSpans(ScamScenario scenario) {
        String message = scenario.getMessage();
        spannableMessage = new SpannableString(message);

        // Apply very faint underlines to red flag regions
        // This is intentionally subtle — not a giveaway, just a hint that
        // the text is interactive. Players still need to find the right words.
        if (scenario.getRedFlags() != null) {
            for (ScamScenario.RedFlag flag : scenario.getRedFlags()) {
                spannableMessage.setSpan(
                        new android.text.style.UnderlineSpan(),
                        flag.getStart(),
                        flag.getEnd(),
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                );
            }
        }

        binding.tvMessage.setText(spannableMessage);
        binding.tvMessage.setMovementMethod(null); // We handle touch manually

        // ── Custom touch listener ──────────────────────────────────
        // Maps finger position to a character offset in the TextView
        binding.tvMessage.setOnTouchListener((v, event) -> {
            if (event.getAction() != MotionEvent.ACTION_UP) return false;

            TextView textView = (TextView) v;
            int offset = getCharacterOffset(textView, event);
            if (offset < 0) return false;

            handleTap(offset);
            return true;
        });
    }

    /**
     * Converts a touch event (x, y) on a TextView into a character offset.
     * This is the standard way to map finger position → text character in Android.
     */
    private int getCharacterOffset(TextView textView, MotionEvent event) {
        int x = (int) event.getX() - textView.getTotalPaddingLeft()
                + textView.getScrollX();
        int y = (int) event.getY() - textView.getTotalPaddingTop()
                + textView.getScrollY();

        android.text.Layout layout = textView.getLayout();
        if (layout == null) return -1;

        int line = layout.getLineForVertical(y);
        return layout.getOffsetForHorizontal(line, x);
    }

    /**
     * Called after every tap on the message text.
     * Asks the engine if the tap hit a red flag, then updates the UI.
     */
    private void handleTap(int characterOffset) {
        int result = engine.onTextTapped(characterOffset);

        if (result >= 0) {
            // ✅ New red flag found!
            ScamScenario.RedFlag flag = engine.getScenario().getRedFlags().get(result);
            highlightFoundFlag(flag, result);
            updateFlagCounter();
            showTapFeedback("🚩 Red Flag Found: " + flag.getLabel(), true);

            // Auto-submit if all flags found
            if (engine.allFlagsFound()) {
                new Handler(Looper.getMainLooper()).postDelayed(
                        this::submitGame, 800);
            }

        } else if (result == -2) {
            // Already found this one
            showTapFeedback("Already flagged!", false);
        } else {
            // Tapped empty space
            showTapFeedback("Nothing suspicious here. Keep looking!", false);
        }
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
        binding.tvResultScore.setText(
                engine.getFoundCount() + " / " + engine.getTotalFlags() + " Red Flags Found");
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
            if (isAdded()) binding.tvTapFeedback.setVisibility(View.INVISIBLE);
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
     * A hardcoded demo scenario used as a fallback.
     * This also serves as the template for adding scenarios to Firestore.
     *
     * Red flag positions are CHARACTER INDICES into the message string.
     * Count them carefully when adding new scenarios!
     */
    private ScamScenario buildDemoScenario() {
        ScamScenario s = new ScamScenario();
        s.setTitle("Suspicious Bank SMS");
        s.setCategory("Phishing");
        s.setScenarioType("sms");
        s.setSenderLabel("Vietcombank");
        s.setXpReward(Constants.XP_PER_SCAM_LAB);

        // The message — count characters to find flag indices
        String msg =
                "Tài khoản VCB của bạn đã bị khóa. " +
                        "Truy cập http://vcb-secure.tk/verify " +
                        "để xác minh ngay. " +
                        "Nhập mã OTP: 482910 để xác nhận. " +
                        "Hỗ trợ: 1900.fake.vietcombank.com";
        s.setMessage(msg);

        // Red Flag 1: Fake URL (positions 35–63)
        ScamScenario.RedFlag flag1 = new ScamScenario.RedFlag();
        flag1.setStart(35);
        flag1.setEnd(63);
        flag1.setLabel("Fake Domain");
        flag1.setExplanation(
                "\"vcb-secure.tk\" is NOT a real Vietcombank domain. " +
                        "The real domain is vietcombank.com.vn. " +
                        ".tk domains are free and commonly used by scammers.");

        // Red Flag 2: Asking for OTP (positions 83–104)
        ScamScenario.RedFlag flag2 = new ScamScenario.RedFlag();
        flag2.setStart(83);
        flag2.setEnd(104);
        flag2.setLabel("OTP in Message");
        flag2.setExplanation(
                "Real banks NEVER send your OTP inside an SMS. " +
                        "An OTP is a one-time password YOU generate or receive to log in — " +
                        "any message containing an OTP is asking you to confirm a transaction " +
                        "you didn't make.");

        // Red Flag 3: Fake hotline URL (positions 109–143)
        ScamScenario.RedFlag flag3 = new ScamScenario.RedFlag();
        flag3.setStart(109);
        flag3.setEnd(143);
        flag3.setLabel("Fake Support URL");
        flag3.setExplanation(
                "The support link contains \"fake\" in the domain and is not " +
                        "an official Vietcombank address. Scammers insert bank names " +
                        "into fake URLs to look legitimate.");

        List<ScamScenario.RedFlag> flags = new ArrayList<>();
        flags.add(flag1);
        flags.add(flag2);
        flags.add(flag3);
        s.setRedFlags(flags);

        return s;
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

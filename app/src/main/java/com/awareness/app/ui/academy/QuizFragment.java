package com.awareness.app.ui.academy;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.awareness.app.R;
import com.awareness.app.databinding.FragmentQuizBinding;
import com.awareness.app.model.QuizQuestion;
import com.awareness.app.utils.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Daily Quiz: 3 questions per day, the same 3 for every user on a given date.
 *
 *  - Questions come from the Firestore "quizzes" collection (published = true). If there are
 *    fewer than 3, the built-in DefaultQuizBank is used so the feature always works.
 *  - Each correct answer is worth 5 XP (Constants.XP_PER_DAILY_QUIZ = 15 for a perfect score).
 *  - One play per day: the result is stored at users/{uid}/quizResults/{yyyy-MM-dd}. The
 *    firestore.rules make that document create-only, so a second attempt is rejected by the server.
 */
public class QuizFragment extends Fragment {

    private static final int QUESTIONS_PER_DAY = 3;
    private static final int XP_PER_CORRECT = Constants.XP_PER_DAILY_QUIZ / QUESTIONS_PER_DAY;

    private FragmentQuizBinding binding;
    private FirebaseFirestore db;
    private DocumentReference userRef;
    private String today;

    private MaterialButton[] optionButtons;
    private List<QuizQuestion> questions = new ArrayList<>();
    private int index = 0;
    private int score = 0;
    private boolean answered = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentQuizBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        // Locale.US keeps ASCII digits; firestore.rules checks this id with a digits-only pattern
        today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            userRef = db.collection(Constants.COLLECTION_USERS).document(user.getUid());
        }
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.btnDone.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        optionButtons = new MaterialButton[]{
                binding.btnOption0, binding.btnOption1, binding.btnOption2, binding.btnOption3};
        for (int i = 0; i < optionButtons.length; i++) {
            final int choice = i;
            optionButtons[i].setOnClickListener(v -> onAnswer(choice));
        }
        binding.btnNext.setOnClickListener(v -> onNext());

        if (userRef == null) {
            showDone("🔒", "Please sign in", "", "", "You need to be signed in to play the daily quiz.");
            return;
        }
        checkAlreadyPlayed();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Loading ───────────────────────────────────────────────────

    private void checkAlreadyPlayed() {
        showLoading();
        userRef.collection("quizResults").document(today).get()
                .addOnSuccessListener(doc -> {
                    if (binding == null) return;
                    if (doc.exists()) {
                        long s  = doc.getLong("score")    != null ? doc.getLong("score")    : 0;
                        long t  = doc.getLong("total")    != null ? doc.getLong("total")    : QUESTIONS_PER_DAY;
                        long xp = doc.getLong("xpEarned") != null ? doc.getLong("xpEarned") : 0;
                        showDone("✅", "Already done today!",
                                "You got " + s + " of " + t + " right",
                                "+" + xp + " XP earned",
                                "Come back tomorrow for 3 new questions.");
                    } else {
                        loadQuestions();
                    }
                })
                .addOnFailureListener(e -> {
                    if (binding != null) loadQuestions();   // offline: let them play; the server still enforces 1/day
                });
    }

    private void loadQuestions() {
        db.collection(Constants.COLLECTION_QUIZZES)
                .whereEqualTo("published", true)
                .get()
                .addOnSuccessListener(snap -> {
                    List<QuizQuestion> pool = new ArrayList<>();
                    for (QueryDocumentSnapshot d : snap) {
                        QuizQuestion q = d.toObject(QuizQuestion.class);
                        if (q.isValid()) pool.add(q);
                    }
                    if (pool.size() < QUESTIONS_PER_DAY) pool = DefaultQuizBank.get();
                    startQuiz(pool);
                })
                .addOnFailureListener(e -> startQuiz(DefaultQuizBank.get()));
    }

    /** Same date -> same 3 questions for everyone (stable sort, then shuffle seeded by the date). */
    private void startQuiz(List<QuizQuestion> pool) {
        if (binding == null) return;

        List<QuizQuestion> sorted = new ArrayList<>(pool);
        Collections.sort(sorted, (a, b) -> String.valueOf(a.getId()).compareTo(String.valueOf(b.getId())));
        Collections.shuffle(sorted, new Random(today.hashCode()));

        questions = new ArrayList<>(sorted.subList(0, Math.min(QUESTIONS_PER_DAY, sorted.size())));
        index = 0;
        score = 0;
        showQuestion();
    }

    // ── Playing ───────────────────────────────────────────────────

    private void showQuestion() {
        QuizQuestion q = questions.get(index);
        answered = false;

        binding.progressBar.setVisibility(View.GONE);
        binding.layoutDone.setVisibility(View.GONE);
        binding.layoutPlaying.setVisibility(View.VISIBLE);

        binding.tvProgress.setText("Question " + (index + 1) + " of " + questions.size());
        binding.progressQuiz.setProgressCompat(index * 100 / questions.size(), true);

        if (q.getCategory() != null && !q.getCategory().isEmpty()) {
            binding.tvCategory.setText(q.getCategory());
            binding.tvCategory.setVisibility(View.VISIBLE);
        } else {
            binding.tvCategory.setVisibility(View.GONE);
        }
        binding.tvQuestion.setText(q.getQuestion());

        for (int i = 0; i < optionButtons.length; i++) {
            if (i < q.getOptions().size()) {
                optionButtons[i].setVisibility(View.VISIBLE);
                optionButtons[i].setText(q.getOptions().get(i));
                styleOption(optionButtons[i], R.color.surface_variant, R.color.text_primary);
            } else {
                optionButtons[i].setVisibility(View.GONE);
            }
        }

        binding.cardExplanation.setVisibility(View.GONE);
        binding.btnNext.setVisibility(View.GONE);
        binding.btnNext.setEnabled(true);
    }

    private void onAnswer(int selected) {
        if (answered || binding == null) return;
        answered = true;

        QuizQuestion q = questions.get(index);
        boolean correct = selected == q.getCorrectIndex();
        if (correct) score++;

        styleOption(optionButtons[q.getCorrectIndex()], R.color.guardian_green, R.color.white);
        if (!correct) styleOption(optionButtons[selected], R.color.error_red, R.color.white);

        binding.tvResultLabel.setText(correct ? "✅ Correct!  +" + XP_PER_CORRECT + " XP" : "❌ Not quite");
        binding.tvResultLabel.setTextColor(ContextCompat.getColor(requireContext(),
                correct ? R.color.guardian_green : R.color.error_red));
        binding.tvExplanation.setText(q.getExplanation() != null ? q.getExplanation() : "");
        binding.cardExplanation.setVisibility(View.VISIBLE);

        boolean last = index == questions.size() - 1;
        binding.btnNext.setText(last ? "Finish" : "Next question");
        binding.btnNext.setVisibility(View.VISIBLE);
    }

    private void onNext() {
        if (binding == null) return;
        if (index + 1 < questions.size()) {
            index++;
            showQuestion();
        } else {
            finishQuiz();
        }
    }

    private void styleOption(MaterialButton button, @ColorRes int background, @ColorRes int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), background)));
        button.setTextColor(ContextCompat.getColor(requireContext(), text));
    }

    // ── Finishing: save result, then award XP ─────────────────────

    private void finishQuiz() {
        binding.btnNext.setEnabled(false);

        final int total = questions.size();
        final int xp = score * XP_PER_CORRECT;

        Map<String, Object> result = new HashMap<>();
        result.put("score", score);
        result.put("total", total);
        result.put("xpEarned", xp);
        result.put("completedAt", FieldValue.serverTimestamp());

        // create-only per day (see firestore.rules): the server refuses a second result for today
        userRef.collection("quizResults").document(today).set(result)
                .addOnSuccessListener(v -> awardXp(xp, total))
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    showDone("⚠️", "Couldn't save your result", "", "",
                            "You may have already played today, or you're offline. Try again later.");
                });
    }

    private void awardXp(int xp, int total) {
        userRef.update(
                "xpPoints", FieldValue.increment(xp),
                "quizzesCompleted", FieldValue.increment(1)
        ).addOnSuccessListener(v -> {
            checkLevelUp();
            if (binding != null) showResult(xp, total, true);
        }).addOnFailureListener(e -> {
            if (binding != null) showResult(0, total, false);
        });
    }

    /** Same formula as LessonDetailFragment: next level needs (level + 1) * 100 XP. */
    private void checkLevelUp() {
        userRef.get().addOnSuccessListener(doc -> {
            Long xp = doc.getLong("xpPoints");
            Long level = doc.getLong("level");
            if (xp == null) return;
            if (level == null) level = 1L;
            if (xp >= (level + 1) * 100L) {
                userRef.update("level", FieldValue.increment(1));
            }
        });
    }

    private void showResult(int xp, int total, boolean xpSaved) {
        String emoji, title;
        if (score == total)          { emoji = "🎉"; title = "Perfect score!"; }
        else if (score * 2 >= total) { emoji = "👏"; title = "Nice work!"; }
        else                         { emoji = "📚"; title = "Keep learning!"; }

        showDone(emoji, title,
                "You got " + score + " of " + total + " right",
                xpSaved ? "+" + xp + " XP" : "XP couldn't be saved",
                "Come back tomorrow for 3 new questions.");
    }

    // ── States ────────────────────────────────────────────────────

    private void showLoading() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.layoutPlaying.setVisibility(View.GONE);
        binding.layoutDone.setVisibility(View.GONE);
    }

    private void showDone(String emoji, String title, String scoreLine, String xpLine, String note) {
        if (binding == null) return;
        binding.progressBar.setVisibility(View.GONE);
        binding.layoutPlaying.setVisibility(View.GONE);
        binding.layoutDone.setVisibility(View.VISIBLE);

        binding.tvDoneEmoji.setText(emoji);
        binding.tvDoneTitle.setText(title);
        binding.tvDoneScore.setText(scoreLine);
        binding.tvDoneScore.setVisibility(scoreLine.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvDoneXp.setText(xpLine);
        binding.tvDoneXp.setVisibility(xpLine.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvDoneNote.setText(note);
    }
}

package com.awareness.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.R;
import com.awareness.app.adapter.CmsLessonAdapter;
import com.awareness.app.adapter.CmsQuizAdapter;
import com.awareness.app.adapter.CmsScenarioAdapter;
import com.awareness.app.databinding.DialogLessonEditBinding;
import com.awareness.app.databinding.DialogQuizEditBinding;
import com.awareness.app.databinding.DialogScenarioEditBinding;
import com.awareness.app.databinding.ItemFlagEditBinding;
import com.awareness.app.databinding.FragmentCmsBinding;
import com.awareness.app.model.Lesson;
import com.awareness.app.model.QuizQuestion;
import com.awareness.app.model.ScamScenario;
import com.awareness.app.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin → Content tab: create, edit, publish/unpublish and delete lessons.
 *
 * Every NEW lesson is a TRUE/FALSE question (statement + correct answer + explanation).
 * Older text / image / video lessons keep their original form when edited.
 *
 * The second tab, "Daily Quiz", manages the multiple-choice questions in the Firestore
 * "quizzes" collection that QuizFragment draws from (3 published questions per day).
 *
 * The third tab, "Scam Lab", manages the fake-scam-message scenarios in the "scam_scenarios"
 * collection that ScamLabFragment plays (tap the suspicious words, 2 wrong taps end the game).
 *
 * Media is entered as an https URL (e.g. a Firebase Storage download link). Uploading files
 * from the app is a possible next step.
 *
 * Who may write is enforced by firestore.rules (lessons: staff/admin only).
 */
public class CmsFragment extends Fragment implements CmsLessonAdapter.Listener {

    private static final String[] CATEGORIES = {
            Constants.CATEGORY_PHISHING, Constants.CATEGORY_VOIP, Constants.CATEGORY_DEEPFAKE,
            Constants.CATEGORY_ROMANCE, Constants.CATEGORY_INVESTMENT
    };
    private static final String[] TYPES = {
            Constants.LESSON_TYPE_TEXT, Constants.LESSON_TYPE_IMAGE, Constants.LESSON_TYPE_VIDEO
    };

    private FragmentCmsBinding binding;
    private FirebaseFirestore db;
    private CmsLessonAdapter adapter;
    private static final String[] QUIZ_CATEGORIES = {
            "None", Constants.CATEGORY_PHISHING, Constants.CATEGORY_VOIP, Constants.CATEGORY_DEEPFAKE,
            Constants.CATEGORY_ROMANCE, Constants.CATEGORY_INVESTMENT
    };
    /** QuizFragment picks this many questions per day; with fewer published it uses its built-in bank. */
    private static final int QUIZ_MIN_POOL = 3;

    // Scam Lab scenarios
    private static final String SCENARIOS = "scam_scenarios";
    private static final String[] SCENARIO_TYPES = {"sms", "email", "webpage"};
    /** Base XP (10-50) + 5 per flag must stay under the 100 XP-per-write cap in firestore.rules. */
    private static final int MAX_FLAGS = 8;

    private static final int TAB_LESSONS = 0;
    private static final int TAB_QUIZ = 1;
    private static final int TAB_SCAM_LAB = 2;

    private ListenerRegistration registration;
    private ListenerRegistration quizRegistration;
    private CmsQuizAdapter quizAdapter;
    private List<Lesson> current = new ArrayList<>();
    private List<QuizQuestion> quizzes = new ArrayList<>();
    private int currentTab = TAB_LESSONS;
    private ListenerRegistration scenarioRegistration;
    private CmsScenarioAdapter scenarioAdapter;
    private List<ScamScenario> scenarios = new ArrayList<>();
    private boolean scenariosLoaded = false;
    private String scenariosError = null;
    private boolean lessonsLoaded = false;
    private boolean quizzesLoaded = false;
    private String lessonsError = null;
    private String quizzesError = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCmsBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CmsLessonAdapter(this);
        quizAdapter = new CmsQuizAdapter(new CmsQuizAdapter.Listener() {
            @Override public void onEdit(QuizQuestion q)   { showQuizEditor(q); }
            @Override public void onDelete(QuizQuestion q) { confirmDeleteQuiz(q); }
            @Override public void onTogglePublished(QuizQuestion q, boolean published) {
                setQuizPublished(q, published);
            }
        });

        scenarioAdapter = new CmsScenarioAdapter(new CmsScenarioAdapter.Listener() {
            @Override public void onEdit(ScamScenario sc)   { showScenarioEditor(sc); }
            @Override public void onDelete(ScamScenario sc) { confirmDeleteScenario(sc); }
            @Override public void onTogglePublished(ScamScenario sc, boolean published) {
                setScenarioPublished(sc, published);
            }
        });

        binding.rvLessons.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLessons.setAdapter(adapter);

        binding.fabAdd.setOnClickListener(v -> {
            if (currentTab == TAB_QUIZ)          showQuizEditor(null);
            else if (currentTab == TAB_SCAM_LAB) showScenarioEditor(null);
            else                                 showEditor(null);
        });

        binding.tabContent.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            @Override public void onTabUnselected(TabLayout.Tab tab) { }
            @Override public void onTabReselected(TabLayout.Tab tab) { }
        });

        startListening();
        startListeningQuizzes();
        startListeningScenarios();
        showTab(TAB_LESSONS);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) registration.remove();
        if (quizRegistration != null) quizRegistration.remove();
        if (scenarioRegistration != null) scenarioRegistration.remove();
        binding = null;
    }

    // ── Tabs ──────────────────────────────────────────────────────

    private void showTab(int newTab) {
        currentTab = newTab;
        if (newTab == TAB_QUIZ)          binding.rvLessons.setAdapter(quizAdapter);
        else if (newTab == TAB_SCAM_LAB) binding.rvLessons.setAdapter(scenarioAdapter);
        else                             binding.rvLessons.setAdapter(adapter);

        binding.fabAdd.setText(newTab == TAB_QUIZ ? "New question"
                : newTab == TAB_SCAM_LAB ? "New scenario" : "New lesson");
        binding.tvQuizHint.setVisibility(newTab == TAB_LESSONS ? View.GONE : View.VISIBLE);
        refreshState();
    }

    /** Spinner / empty message / error text / hint line for whichever tab is showing. */
    private void refreshState() {
        if (binding == null) return;

        boolean loaded;
        String error;
        int count;
        String noun;
        switch (currentTab) {
            case TAB_QUIZ:
                loaded = quizzesLoaded; error = quizzesError; count = quizzes.size();
                noun = "quiz questions";
                break;
            case TAB_SCAM_LAB:
                loaded = scenariosLoaded; error = scenariosError; count = scenarios.size();
                noun = "Scam Lab scenarios";
                break;
            default:
                loaded = lessonsLoaded; error = lessonsError; count = current.size();
                noun = "lessons";
        }

        binding.progressBar.setVisibility(loaded ? View.GONE : View.VISIBLE);

        if (error != null) {
            binding.tvEmpty.setText("Couldn't load " + noun + ".\n" + error);
            binding.tvEmpty.setVisibility(View.VISIBLE);
        } else if (loaded && count == 0) {
            binding.tvEmpty.setText(emptyMessage());
            binding.tvEmpty.setVisibility(View.VISIBLE);
        } else {
            binding.tvEmpty.setVisibility(View.GONE);
        }

        if (currentTab == TAB_QUIZ) updateQuizHint();
        else if (currentTab == TAB_SCAM_LAB) updateScenarioHint();
    }

    private String emptyMessage() {
        switch (currentTab) {
            case TAB_QUIZ:
                return "No quiz questions yet.\nTap “New question” to add one.\n\nUntil at least "
                        + QUIZ_MIN_POOL + " are published, learners get the 10 built-in questions.";
            case TAB_SCAM_LAB:
                return "No Scam Lab scenarios yet.\nTap “New scenario” to add one.\n\n"
                        + "Until one is published, players get the built-in demo scenario.";
            default:
                return "No lessons yet.\nTap “New lesson” to create the first one.";
        }
    }

    private void updateQuizHint() {
        int usable = 0;
        for (QuizQuestion q : quizzes) if (q.isPublished() && q.isValid()) usable++;

        binding.tvQuizHint.setText(usable >= QUIZ_MIN_POOL
                ? "✅ " + usable + " published questions. Each day the app picks " + QUIZ_MIN_POOL
                    + " of them, the same ones for every learner."
                : "⚠️ " + usable + " published, but at least " + QUIZ_MIN_POOL + " are needed. Until then "
                    + "learners get the 10 built-in questions instead of yours.");
    }

    private void updateScenarioHint() {
        int usable = 0;
        for (ScamScenario sc : scenarios) {
            if (sc.isPublished() && sc.getRedFlags() != null && !sc.getRedFlags().isEmpty()) usable++;
        }
        binding.tvQuizHint.setText(usable > 0
                ? "✅ " + usable + " published scenario" + (usable == 1 ? "" : "s")
                    + ". Each game picks one of them at random."
                : "ℹ️ Nothing published yet, so Scam Lab uses its one built-in demo scenario.");
    }

    // ── Live lists ────────────────────────────────────────────────

    private void startListening() {
        // Staff can read drafts too (see lessons rule); learners only get published ones.
        registration = db.collection(Constants.COLLECTION_LESSONS)
                .orderBy("orderIndex")
                .addSnapshotListener((snap, error) -> {
                    if (binding == null) return;
                    lessonsLoaded = true;

                    if (error != null || snap == null) {
                        lessonsError = error != null ? error.getMessage() : "unknown error";
                        refreshState();
                        return;
                    }
                    lessonsError = null;
                    current = new ArrayList<>(snap.toObjects(Lesson.class));
                    adapter.submit(current);
                    refreshState();
                });
    }

    private void startListeningQuizzes() {
        // Same rule as lessons: staff see drafts, learners only published questions.
        quizRegistration = db.collection(Constants.COLLECTION_QUIZZES)
                .addSnapshotListener((snap, error) -> {
                    if (binding == null) return;
                    quizzesLoaded = true;

                    if (error != null || snap == null) {
                        quizzesError = error != null ? error.getMessage() : "unknown error";
                        refreshState();
                        return;
                    }
                    quizzesError = null;

                    // Convert one by one: a hand-edited document with a wrong field type is skipped
                    // instead of crashing the whole list.
                    List<QuizQuestion> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            QuizQuestion q = doc.toObject(QuizQuestion.class);
                            if (q != null) list.add(q);
                        } catch (RuntimeException ignored) { }
                    }
                    Collections.sort(list, (x, y) ->
                            safe(x.getQuestion()).compareToIgnoreCase(safe(y.getQuestion())));
                    quizzes = list;
                    quizAdapter.submit(quizzes);
                    refreshState();
                });
    }

    private void startListeningScenarios() {
        // Staff see drafts too (scam_scenarios rule); players only get published scenarios.
        scenarioRegistration = db.collection(SCENARIOS)
                .addSnapshotListener((snap, error) -> {
                    if (binding == null) return;
                    scenariosLoaded = true;

                    if (error != null || snap == null) {
                        scenariosError = error != null ? error.getMessage() : "unknown error";
                        refreshState();
                        return;
                    }
                    scenariosError = null;

                    // One by one: a hand-edited document with a wrong field type is skipped, not fatal
                    List<ScamScenario> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snap.getDocuments()) {
                        try {
                            ScamScenario sc = doc.toObject(ScamScenario.class);
                            if (sc != null) list.add(sc);
                        } catch (RuntimeException ignored) { }
                    }
                    Collections.sort(list, (x, y) -> safe(x.getTitle()).compareToIgnoreCase(safe(y.getTitle())));
                    scenarios = list;
                    scenarioAdapter.submit(scenarios);
                    refreshState();
                });
    }

    // ── Row actions ───────────────────────────────────────────────

    @Override
    public void onEdit(Lesson lesson) {
        showEditor(lesson);
    }

    @Override
    public void onDelete(Lesson lesson) {
        if (lesson.getLessonId() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this lesson?")
                .setMessage("“" + lesson.getTitle() + "” will be removed for everyone. "
                        + "Tip: unpublish it instead if you might want it back.")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection(Constants.COLLECTION_LESSONS).document(lesson.getLessonId())
                                .delete()
                                .addOnFailureListener(e -> toast("Delete failed: " + e.getMessage())))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onTogglePublished(Lesson lesson, boolean published) {
        if (lesson.getLessonId() == null) return;
        db.collection(Constants.COLLECTION_LESSONS).document(lesson.getLessonId())
                .update("published", published)
                .addOnFailureListener(e -> {
                    toast("Couldn't change status: " + e.getMessage());
                    adapter.submit(current);   // put the switch back
                });
    }

    // ── Create / edit dialog ──────────────────────────────────────

    private void showEditor(@Nullable Lesson editing) {
        DialogLessonEditBinding d = DialogLessonEditBinding.inflate(getLayoutInflater());

        // Every NEW lesson is a true/false question. Older text/image/video lessons keep their
        // original form when edited, so nothing already in Firestore breaks.
        final boolean trueFalse = editing == null || Lesson.TYPE_TRUE_FALSE.equals(editing.getType());
        d.groupTrueFalse.setVisibility(trueFalse ? View.VISIBLE : View.GONE);
        d.groupLegacy.setVisibility(trueFalse ? View.GONE : View.VISIBLE);

        d.actvCategory.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, CATEGORIES));
        d.actvType.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, TYPES));

        if (editing != null) {
            d.etTitle.setText(editing.getTitle());
            d.actvCategory.setText(editing.getCategory(), false);
            d.etXp.setText(String.valueOf(editing.getXpReward()));
            d.etOrder.setText(String.valueOf(editing.getOrderIndex()));
            d.switchPublished.setChecked(editing.isPublished());

            if (trueFalse) {
                d.etStatement.setText(editing.getContent());
                d.etExplanation.setText(editing.getExplanation());
                if (editing.getCorrectAnswer() != null) {
                    d.toggleAnswer.check(editing.getCorrectAnswer()
                            ? R.id.btnAnswerTrue : R.id.btnAnswerFalse);
                }
            } else {
                d.actvType.setText(editing.getType(), false);
                d.etContent.setText(editing.getContent());
                d.etMediaUrl.setText(editing.getMediaUrl());
                d.etThumbUrl.setText(editing.getThumbnailUrl());
            }
        } else {
            d.actvCategory.setText(CATEGORIES[0], false);
            d.etXp.setText(String.valueOf(Constants.XP_PER_LESSON));
            d.etOrder.setText(String.valueOf(nextOrderIndex()));
            d.switchPublished.setChecked(false);   // new lessons start as drafts
        }

        updateMediaFieldVisibility(d);
        d.actvType.setOnItemClickListener((parent, v, position, id) -> updateMediaFieldVisibility(d));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing == null ? "New true/false question"
                        : trueFalse ? "Edit question" : "Edit lesson")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)      // real handler below, so we can keep it open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> validateAndSave(d, editing, dialog, trueFalse));
    }

    private void updateMediaFieldVisibility(DialogLessonEditBinding d) {
        boolean needsMedia = !Constants.LESSON_TYPE_TEXT.equals(textOf(d.actvType));
        d.tilMediaUrl.setVisibility(needsMedia ? View.VISIBLE : View.GONE);
    }

    private void validateAndSave(DialogLessonEditBinding d, @Nullable Lesson editing,
                                 AlertDialog dialog, boolean trueFalse) {
        // Clear old errors
        d.tilTitle.setError(null);
        d.tilCategory.setError(null);
        d.tilStatement.setError(null);
        d.tvAnswerError.setVisibility(View.GONE);
        d.tilExplanation.setError(null);
        d.tilType.setError(null);
        d.tilContent.setError(null);
        d.tilMediaUrl.setError(null);
        d.tilThumbUrl.setError(null);
        d.tilXp.setError(null);
        d.tilOrder.setError(null);

        String title    = textOf(d.etTitle);
        String category = textOf(d.actvCategory);
        Integer xp      = parseInt(textOf(d.etXp));
        Integer order   = parseInt(textOf(d.etOrder));

        boolean ok = true;
        if (title.length() < 3 || title.length() > 100) {
            d.tilTitle.setError("3–100 characters"); ok = false;
        }
        if (!contains(CATEGORIES, category)) {
            d.tilCategory.setError("Pick a category"); ok = false;
        }
        // Max 50: firestore.rules lets a user gain at most 100 XP per write
        if (xp == null || xp < 5 || xp > 50) {
            d.tilXp.setError("5–50"); ok = false;
        }
        if (order == null || order < 0) {
            d.tilOrder.setError("0 or higher"); ok = false;
        }

        // Keys match the Lesson model / existing queries ("published", "orderIndex", …)
        Map<String, Object> data = new HashMap<>();

        if (trueFalse) {
            String statement   = textOf(d.etStatement);
            String explanation = textOf(d.etExplanation);
            int checked        = d.toggleAnswer.getCheckedButtonId();

            if (statement.length() < 10 || statement.length() > 500) {
                d.tilStatement.setError("10–500 characters"); ok = false;
            }
            if (checked != R.id.btnAnswerTrue && checked != R.id.btnAnswerFalse) {
                d.tvAnswerError.setVisibility(View.VISIBLE); ok = false;
            }
            if (explanation.length() < 10 || explanation.length() > 1000) {
                d.tilExplanation.setError("10–1,000 characters"); ok = false;
            }
            if (!ok) return;

            data.put("type", Lesson.TYPE_TRUE_FALSE);
            data.put("content", statement);                 // the statement the learner judges
            data.put("explanation", explanation);
            data.put("correctAnswer", checked == R.id.btnAnswerTrue);
            data.put("mediaUrl", null);
            data.put("thumbnailUrl", null);
        } else {
            // Older lessons: unchanged text / image / video form
            String type      = textOf(d.actvType);
            String content   = textOf(d.etContent);
            String mediaUrl  = textOf(d.etMediaUrl);
            String thumbUrl  = textOf(d.etThumbUrl);
            boolean needsMedia = !Constants.LESSON_TYPE_TEXT.equals(type);

            if (!contains(TYPES, type)) {
                d.tilType.setError("Pick a type"); ok = false;
            }
            if (content.length() < 20 || content.length() > 20000) {
                d.tilContent.setError("20–20,000 characters"); ok = false;
            }
            if (needsMedia && !isHttps(mediaUrl)) {
                d.tilMediaUrl.setError("Enter a full https:// link"); ok = false;
            }
            if (!thumbUrl.isEmpty() && !isHttps(thumbUrl)) {
                d.tilThumbUrl.setError("Must start with https://"); ok = false;
            }
            if (!ok) return;

            data.put("type", type);
            data.put("content", content);
            data.put("mediaUrl", needsMedia ? mediaUrl : null);
            data.put("thumbnailUrl", thumbUrl.isEmpty() ? null : thumbUrl);
        }

        data.put("title", title);
        data.put("category", category);
        data.put("xpReward", xp);
        data.put("orderIndex", order);
        data.put("published", d.switchPublished.isChecked());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        com.google.android.gms.tasks.Task<?> task;
        if (editing == null || editing.getLessonId() == null) {
            data.put("createdAt", FieldValue.serverTimestamp());
            task = db.collection(Constants.COLLECTION_LESSONS).add(data);
        } else {
            task = db.collection(Constants.COLLECTION_LESSONS)
                    .document(editing.getLessonId()).update(data);
        }

        task.addOnSuccessListener(r -> {
            dialog.dismiss();
            toast(editing == null ? "Question created." : "Saved.");
        }).addOnFailureListener(e -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            toast("Save failed: " + e.getMessage());
        });
    }

    // ── Daily quiz questions ──────────────────────────────────────

    private void confirmDeleteQuiz(QuizQuestion q) {
        if (q.getId() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this question?")
                .setMessage("It will be removed from the daily quiz for everyone. "
                        + "Tip: unpublish it instead if you might want it back.")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection(Constants.COLLECTION_QUIZZES).document(q.getId())
                                .delete()
                                .addOnFailureListener(e -> toast("Delete failed: " + e.getMessage())))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setQuizPublished(QuizQuestion q, boolean published) {
        if (q.getId() == null) return;
        if (published && !q.isValid()) {
            toast("Edit this question first: it needs a correct answer and 2-4 options.");
            quizAdapter.submit(quizzes);      // put the switch back
            return;
        }
        db.collection(Constants.COLLECTION_QUIZZES).document(q.getId())
                .update("published", published)
                .addOnFailureListener(e -> {
                    toast("Couldn't change status: " + e.getMessage());
                    quizAdapter.submit(quizzes);
                });
    }

    private void showQuizEditor(@Nullable QuizQuestion editing) {
        DialogQuizEditBinding d = DialogQuizEditBinding.inflate(getLayoutInflater());

        d.actvCategory.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, QUIZ_CATEGORIES));

        EditText[] optionFields = {d.etOptionA, d.etOptionB, d.etOptionC, d.etOptionD};
        int[] correctIds = {R.id.btnCorrectA, R.id.btnCorrectB, R.id.btnCorrectC, R.id.btnCorrectD};

        if (editing != null) {
            d.etQuestion.setText(editing.getQuestion());
            List<String> opts = editing.getOptions();
            for (int i = 0; i < optionFields.length; i++) {
                if (opts != null && i < opts.size()) optionFields[i].setText(opts.get(i));
            }
            int idx = editing.getCorrectIndex();
            if (idx >= 0 && idx < correctIds.length) d.toggleCorrect.check(correctIds[idx]);
            d.etExplanation.setText(editing.getExplanation());
            boolean hasCategory = editing.getCategory() != null && !editing.getCategory().isEmpty();
            d.actvCategory.setText(hasCategory ? editing.getCategory() : "None", false);
            d.switchPublished.setChecked(editing.isPublished());
        } else {
            d.actvCategory.setText("None", false);
            d.switchPublished.setChecked(false);   // new questions start as drafts
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing == null ? "New daily quiz question" : "Edit quiz question")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)      // real handler below, so we can keep it open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> validateAndSaveQuiz(d, editing, dialog));
    }

    private void validateAndSaveQuiz(DialogQuizEditBinding d, @Nullable QuizQuestion editing,
                                     AlertDialog dialog) {
        TextInputLayout[] optionLayouts = {d.tilOptionA, d.tilOptionB, d.tilOptionC, d.tilOptionD};
        int[] correctIds = {R.id.btnCorrectA, R.id.btnCorrectB, R.id.btnCorrectC, R.id.btnCorrectD};

        // Clear old errors
        d.tilQuestion.setError(null);
        for (TextInputLayout til : optionLayouts) til.setError(null);
        d.tvCorrectError.setVisibility(View.GONE);
        d.tilExplanation.setError(null);

        String question    = textOf(d.etQuestion);
        String[] opt       = {textOf(d.etOptionA), textOf(d.etOptionB),
                              textOf(d.etOptionC), textOf(d.etOptionD)};
        String explanation = textOf(d.etExplanation);
        String categoryTxt = textOf(d.actvCategory);
        int checked        = d.toggleCorrect.getCheckedButtonId();

        boolean ok = true;

        if (question.length() < 10 || question.length() > 300) {
            d.tilQuestion.setError("10–300 characters"); ok = false;
        }
        if (opt[0].isEmpty()) { optionLayouts[0].setError("Required"); ok = false; }
        if (opt[1].isEmpty()) { optionLayouts[1].setError("Required"); ok = false; }
        // No gaps: C may only be skipped if D is skipped too, so option positions stay aligned
        if (opt[2].isEmpty() && !opt[3].isEmpty()) {
            optionLayouts[2].setError("Fill option C before option D"); ok = false;
        }
        for (int i = 1; i < opt.length; i++) {
            for (int j = 0; j < i; j++) {
                if (!opt[i].isEmpty() && opt[i].equalsIgnoreCase(opt[j])) {
                    optionLayouts[i].setError("Same as option " + (char) ('A' + j));
                    ok = false;
                    break;
                }
            }
        }

        int correctIndex = -1;
        for (int i = 0; i < correctIds.length; i++) if (checked == correctIds[i]) correctIndex = i;
        if (correctIndex < 0) {
            d.tvCorrectError.setText("Choose the correct option");
            d.tvCorrectError.setVisibility(View.VISIBLE);
            ok = false;
        } else if (opt[correctIndex].isEmpty()) {
            d.tvCorrectError.setText("The option you marked correct is empty");
            d.tvCorrectError.setVisibility(View.VISIBLE);
            ok = false;
        }

        if (explanation.length() < 10 || explanation.length() > 600) {
            d.tilExplanation.setError("10–600 characters"); ok = false;
        }
        if (!ok) return;

        List<String> options = new ArrayList<>();
        for (String o : opt) if (!o.isEmpty()) options.add(o);   // contiguous, so correctIndex still matches

        Map<String, Object> data = new HashMap<>();
        data.put("question", question);
        data.put("options", options);
        data.put("correctIndex", correctIndex);
        data.put("explanation", explanation);
        data.put("category", categoryTxt.isEmpty() || "None".equals(categoryTxt) ? null : categoryTxt);
        data.put("published", d.switchPublished.isChecked());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        com.google.android.gms.tasks.Task<?> task;
        if (editing == null || editing.getId() == null) {
            task = db.collection(Constants.COLLECTION_QUIZZES).add(data);
        } else {
            task = db.collection(Constants.COLLECTION_QUIZZES).document(editing.getId()).update(data);
        }

        task.addOnSuccessListener(r -> {
            dialog.dismiss();
            toast(editing == null ? "Question added." : "Saved.");
        }).addOnFailureListener(e -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            toast("Save failed: " + e.getMessage());
        });
    }

    private static String safe(@Nullable String s) {
        return s == null ? "" : s;
    }

    // ── Scam Lab scenarios ────────────────────────────────────────

    private void confirmDeleteScenario(ScamScenario sc) {
        if (sc.getScenarioId() == null) return;
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete this scenario?")
                .setMessage("“" + safe(sc.getTitle()) + "” will be removed for everyone. "
                        + "Tip: unpublish it instead if you might want it back.")
                .setPositiveButton("Delete", (d, w) ->
                        db.collection(SCENARIOS).document(sc.getScenarioId())
                                .delete()
                                .addOnFailureListener(e -> toast("Delete failed: " + e.getMessage())))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setScenarioPublished(ScamScenario sc, boolean published) {
        if (sc.getScenarioId() == null) return;
        boolean playable = sc.getMessage() != null && !sc.getMessage().trim().isEmpty()
                && sc.getRedFlags() != null && !sc.getRedFlags().isEmpty();
        if (published && !playable) {
            toast("Edit this scenario first: it needs a message and at least one red flag.");
            scenarioAdapter.submit(scenarios);      // put the switch back
            return;
        }
        db.collection(SCENARIOS).document(sc.getScenarioId())
                .update("published", published)
                .addOnFailureListener(e -> {
                    toast("Couldn't change status: " + e.getMessage());
                    scenarioAdapter.submit(scenarios);
                });
    }

    private void showScenarioEditor(@Nullable ScamScenario editing) {
        DialogScenarioEditBinding d = DialogScenarioEditBinding.inflate(getLayoutInflater());
        final List<ItemFlagEditBinding> flagRows = new ArrayList<>();

        d.actvCategory.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, CATEGORIES));
        d.actvType.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, SCENARIO_TYPES));

        if (editing != null) {
            d.etTitle.setText(editing.getTitle());
            d.actvCategory.setText(editing.getCategory(), false);
            d.actvType.setText(editing.getScenarioType() != null ? editing.getScenarioType() : "sms", false);
            d.etSender.setText(editing.getSenderLabel());
            d.etMessage.setText(editing.getMessage());
            d.etXp.setText(String.valueOf(editing.getXpReward()));
            d.switchPublished.setChecked(editing.isPublished());

            String msg = editing.getMessage() != null ? editing.getMessage() : "";
            if (editing.getRedFlags() != null) {
                for (ScamScenario.RedFlag f : editing.getRedFlags()) {
                    String words = f.getText();
                    if ((words == null || words.isEmpty()) && f.getStart() >= 0
                            && f.getEnd() > f.getStart() && f.getEnd() <= msg.length()) {
                        words = msg.substring(f.getStart(), f.getEnd());   // older scenarios stored positions only
                    }
                    addFlagRow(d, flagRows, words != null ? words : "", safe(f.getLabel()),
                            safe(f.getExplanation()));
                }
            }
        } else {
            d.actvCategory.setText(CATEGORIES[0], false);
            d.actvType.setText("sms", false);
            d.etXp.setText(String.valueOf(Constants.XP_PER_SCAM_LAB));
            d.switchPublished.setChecked(false);   // new scenarios start as drafts
        }
        updateFlagCount(d, flagRows);

        d.btnAddFlagFromSelection.setOnClickListener(v -> addFlagFromSelection(d, flagRows));
        d.btnAddFlagManual.setOnClickListener(v -> addFlagRow(d, flagRows, "", "", ""));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing == null ? "New Scam Lab scenario" : "Edit scenario")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)      // real handler below, so we can keep it open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> validateAndSaveScenario(d, flagRows, editing, dialog));
    }

    private void addFlagRow(DialogScenarioEditBinding d, List<ItemFlagEditBinding> rows,
                            String text, String label, String explanation) {
        if (rows.size() >= MAX_FLAGS) {
            toast("A scenario can have up to " + MAX_FLAGS + " red flags.");
            return;
        }
        ItemFlagEditBinding row = ItemFlagEditBinding.inflate(getLayoutInflater(), d.containerFlags, false);
        row.etFlagText.setText(text);
        row.etFlagLabel.setText(label);
        row.etFlagExplanation.setText(explanation);
        row.btnRemoveFlag.setOnClickListener(v -> {
            d.containerFlags.removeView(row.getRoot());
            rows.remove(row);
            updateFlagCount(d, rows);
        });
        d.containerFlags.addView(row.getRoot());
        rows.add(row);
        updateFlagCount(d, rows);
    }

    private void updateFlagCount(DialogScenarioEditBinding d, List<ItemFlagEditBinding> rows) {
        d.tvFlagsHeader.setText("Red flags (" + rows.size() + " of " + MAX_FLAGS + ")");
        d.tvNoFlags.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        if (!rows.isEmpty()) d.tvFlagsError.setVisibility(View.GONE);
    }

    /** Takes the words currently selected in the message box and turns them into a red flag. */
    private void addFlagFromSelection(DialogScenarioEditBinding d, List<ItemFlagEditBinding> rows) {
        CharSequence cs = d.etMessage.getText();
        String message = cs == null ? "" : cs.toString();       // not trimmed: selection indexes refer to this
        int a = d.etMessage.getSelectionStart();
        int b = d.etMessage.getSelectionEnd();

        if (a < 0 || b < 0 || a == b) {
            toast("First select the suspicious words in the message: long-press a word, then drag the handles.");
            return;
        }
        int[] r = snapToWords(message, Math.min(a, b), Math.max(a, b));
        if (r[0] >= r[1]) {
            toast("Select some words, not just spaces.");
            return;
        }
        String words = message.substring(r[0], r[1]);
        for (ItemFlagEditBinding row : rows) {
            if (words.equals(textOf(row.etFlagText))) {
                toast("That one is already in the list.");
                return;
            }
        }
        addFlagRow(d, rows, words, "", "");
    }

    /**
     * Players tap WORDS, so a red flag must cover whole words: widen [start, end) to the nearest
     * spaces on both sides, then trim any spaces at the edges.
     */
    private static int[] snapToWords(String msg, int start, int end) {
        start = Math.max(0, Math.min(start, msg.length()));
        end   = Math.max(start, Math.min(end, msg.length()));
        while (start < end && Character.isWhitespace(msg.charAt(start))) start++;
        while (end > start && Character.isWhitespace(msg.charAt(end - 1))) end--;
        while (start > 0 && !Character.isWhitespace(msg.charAt(start - 1))) start--;
        while (end < msg.length() && !Character.isWhitespace(msg.charAt(end))) end++;
        return new int[]{start, end};
    }

    private void validateAndSaveScenario(DialogScenarioEditBinding d, List<ItemFlagEditBinding> rows,
                                         @Nullable ScamScenario editing, AlertDialog dialog) {
        // Clear old errors
        d.tilTitle.setError(null);
        d.tilCategory.setError(null);
        d.tilType.setError(null);
        d.tilSender.setError(null);
        d.tilMessage.setError(null);
        d.tilXp.setError(null);
        d.tvFlagsError.setVisibility(View.GONE);
        for (ItemFlagEditBinding row : rows) {
            row.tilFlagText.setError(null);
            row.tilFlagLabel.setError(null);
            row.tilFlagExplanation.setError(null);
        }

        String title    = textOf(d.etTitle);
        String category = textOf(d.actvCategory);
        String type     = textOf(d.actvType);
        String sender   = textOf(d.etSender);
        String message  = textOf(d.etMessage);
        Integer xp      = parseInt(textOf(d.etXp));

        boolean ok = true;
        if (title.length() < 3 || title.length() > 80) {
            d.tilTitle.setError("3–80 characters"); ok = false;
        }
        if (!contains(CATEGORIES, category)) {
            d.tilCategory.setError("Pick a category"); ok = false;
        }
        if (!contains(SCENARIO_TYPES, type)) {
            d.tilType.setError("Pick a message type"); ok = false;
        }
        if (sender.length() < 2 || sender.length() > 60) {
            d.tilSender.setError("2–60 characters"); ok = false;
        }
        if (message.length() < 30 || message.length() > 1000) {
            d.tilMessage.setError("30–1,000 characters"); ok = false;
        }
        // 10-50 base + 5 per flag (max 8) stays under the 100 XP-per-write cap in firestore.rules
        if (xp == null || xp < 10 || xp > 50) {
            d.tilXp.setError("10–50"); ok = false;
        }

        // ── Red flags ────────────────────────────────────────────
        if (rows.isEmpty()) {
            d.tvFlagsError.setText("Add at least one red flag.");
            d.tvFlagsError.setVisibility(View.VISIBLE);
            ok = false;
        }

        List<Map<String, Object>> flagMaps = new ArrayList<>();
        List<int[]> ranges = new ArrayList<>();

        for (ItemFlagEditBinding row : rows) {
            String text  = textOf(row.etFlagText);
            String label = textOf(row.etFlagLabel);
            String why   = textOf(row.etFlagExplanation);
            boolean rowOk = true;
            int[] range = null;

            if (text.isEmpty()) {
                row.tilFlagText.setError("Required"); rowOk = false;
            } else {
                int at = message.indexOf(text);
                if (at < 0) {
                    row.tilFlagText.setError("These words aren't in the message. Check spelling and spaces.");
                    rowOk = false;
                } else {
                    // The game and the red flag both work on whole words
                    int[] r = snapToWords(message, at, at + text.length());
                    String snapped = message.substring(r[0], r[1]);

                    if (message.indexOf(snapped) != message.lastIndexOf(snapped)) {
                        row.tilFlagText.setError("These words appear more than once in the message. "
                                + "Use a longer phrase that appears only once.");
                        rowOk = false;
                    } else {
                        for (int[] other : ranges) {
                            if (r[0] < other[1] && r[1] > other[0]) {
                                row.tilFlagText.setError("Overlaps another red flag");
                                rowOk = false;
                                break;
                            }
                        }
                        if (rowOk) range = r;
                    }
                }
            }
            if (label.length() < 2 || label.length() > 40) {
                row.tilFlagLabel.setError("2–40 characters"); rowOk = false;
            }
            if (why.length() < 10 || why.length() > 400) {
                row.tilFlagExplanation.setError("10–400 characters"); rowOk = false;
            }

            if (rowOk && range != null) {
                ranges.add(range);
                Map<String, Object> flag = new HashMap<>();
                flag.put("text", message.substring(range[0], range[1]));   // whole words, as the game will find them
                flag.put("start", range[0]);                               // also saved for older app versions
                flag.put("end", range[1]);
                flag.put("label", label);
                flag.put("explanation", why);
                flagMaps.add(flag);
            } else {
                ok = false;
            }
        }
        if (!ok) return;

        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("category", category);
        data.put("scenarioType", type);
        data.put("senderLabel", sender);
        data.put("message", message);
        data.put("xpReward", xp);
        data.put("redFlags", flagMaps);
        data.put("published", d.switchPublished.isChecked());

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

        com.google.android.gms.tasks.Task<?> task;
        if (editing == null || editing.getScenarioId() == null) {
            task = db.collection(SCENARIOS).add(data);
        } else {
            task = db.collection(SCENARIOS).document(editing.getScenarioId()).update(data);
        }

        task.addOnSuccessListener(r -> {
            dialog.dismiss();
            toast(editing == null ? "Scenario added." : "Saved.");
        }).addOnFailureListener(e -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
            toast("Save failed: " + e.getMessage());
        });
    }

    // ── Helpers ───────────────────────────────────────────────────

    private int nextOrderIndex() {
        int max = -1;
        for (Lesson l : current) max = Math.max(max, l.getOrderIndex());
        return max + 1;
    }

    private static String textOf(android.widget.TextView v) {
        return v.getText() == null ? "" : v.getText().toString().trim();
    }

    private static Integer parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }

    private static boolean contains(String[] arr, String value) {
        for (String s : arr) if (s.equals(value)) return true;
        return false;
    }

    private static boolean isHttps(String url) {
        return url.length() > 8 && url.length() < 2000
                && url.toLowerCase(Locale.US).startsWith("https://");
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}

package com.awareness.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.adapter.CmsLessonAdapter;
import com.awareness.app.databinding.DialogLessonEditBinding;
import com.awareness.app.databinding.FragmentCmsBinding;
import com.awareness.app.model.Lesson;
import com.awareness.app.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Admin → Content tab: create, edit, publish/unpublish and delete lessons.
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
    private ListenerRegistration registration;
    private List<Lesson> current = new ArrayList<>();

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
        binding.rvLessons.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLessons.setAdapter(adapter);
        binding.fabAdd.setOnClickListener(v -> showEditor(null));

        startListening();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (registration != null) registration.remove();
        binding = null;
    }

    // ── Live list ─────────────────────────────────────────────────

    private void startListening() {
        // Staff can read drafts too (see lessons rule); learners only get published ones.
        registration = db.collection(Constants.COLLECTION_LESSONS)
                .orderBy("orderIndex")
                .addSnapshotListener((snap, error) -> {
                    if (binding == null) return;
                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null || snap == null) {
                        binding.tvEmpty.setText("Couldn't load lessons.\n"
                                + (error != null ? error.getMessage() : ""));
                        binding.tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }
                    current = new ArrayList<>(snap.toObjects(Lesson.class));
                    adapter.submit(current);
                    binding.tvEmpty.setText("No lessons yet.\nTap “New lesson” to create the first one.");
                    binding.tvEmpty.setVisibility(current.isEmpty() ? View.VISIBLE : View.GONE);
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

        d.actvCategory.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, CATEGORIES));
        d.actvType.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, TYPES));

        if (editing != null) {
            d.etTitle.setText(editing.getTitle());
            d.actvCategory.setText(editing.getCategory(), false);
            d.actvType.setText(editing.getType(), false);
            d.etContent.setText(editing.getContent());
            d.etMediaUrl.setText(editing.getMediaUrl());
            d.etThumbUrl.setText(editing.getThumbnailUrl());
            d.etXp.setText(String.valueOf(editing.getXpReward()));
            d.etOrder.setText(String.valueOf(editing.getOrderIndex()));
            d.switchPublished.setChecked(editing.isPublished());
        } else {
            d.actvCategory.setText(CATEGORIES[0], false);
            d.actvType.setText(Constants.LESSON_TYPE_TEXT, false);
            d.etXp.setText(String.valueOf(Constants.XP_PER_LESSON));
            d.etOrder.setText(String.valueOf(nextOrderIndex()));
            d.switchPublished.setChecked(false);   // new lessons start as drafts
        }

        updateMediaFieldVisibility(d);
        d.actvType.setOnItemClickListener((parent, v, position, id) -> updateMediaFieldVisibility(d));

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(editing == null ? "New lesson" : "Edit lesson")
                .setView(d.getRoot())
                .setPositiveButton("Save", null)      // real handler below, so we can keep it open on errors
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> validateAndSave(d, editing, dialog));
    }

    private void updateMediaFieldVisibility(DialogLessonEditBinding d) {
        boolean needsMedia = !Constants.LESSON_TYPE_TEXT.equals(textOf(d.actvType));
        d.tilMediaUrl.setVisibility(needsMedia ? View.VISIBLE : View.GONE);
    }

    private void validateAndSave(DialogLessonEditBinding d, @Nullable Lesson editing, AlertDialog dialog) {
        // Clear old errors
        d.tilTitle.setError(null);
        d.tilCategory.setError(null);
        d.tilType.setError(null);
        d.tilContent.setError(null);
        d.tilMediaUrl.setError(null);
        d.tilThumbUrl.setError(null);
        d.tilXp.setError(null);
        d.tilOrder.setError(null);

        String title     = textOf(d.etTitle);
        String category  = textOf(d.actvCategory);
        String type      = textOf(d.actvType);
        String content   = textOf(d.etContent);
        String mediaUrl  = textOf(d.etMediaUrl);
        String thumbUrl  = textOf(d.etThumbUrl);
        Integer xp       = parseInt(textOf(d.etXp));
        Integer order    = parseInt(textOf(d.etOrder));
        boolean needsMedia = !Constants.LESSON_TYPE_TEXT.equals(type);

        boolean ok = true;
        if (title.length() < 3 || title.length() > 100) {
            d.tilTitle.setError("3–100 characters"); ok = false;
        }
        if (!contains(CATEGORIES, category)) {
            d.tilCategory.setError("Pick a category"); ok = false;
        }
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
        // Max 50: firestore.rules lets a user gain at most 100 XP per write
        if (xp == null || xp < 5 || xp > 50) {
            d.tilXp.setError("5–50"); ok = false;
        }
        if (order == null || order < 0) {
            d.tilOrder.setError("0 or higher"); ok = false;
        }
        if (!ok) return;

        // Keys match the Lesson model / existing queries ("published", "orderIndex", …)
        Map<String, Object> data = new HashMap<>();
        data.put("title", title);
        data.put("category", category);
        data.put("type", type);
        data.put("content", content);
        data.put("mediaUrl", needsMedia ? mediaUrl : null);
        data.put("thumbnailUrl", thumbUrl.isEmpty() ? null : thumbUrl);
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
            toast(editing == null ? "Lesson created." : "Lesson saved.");
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

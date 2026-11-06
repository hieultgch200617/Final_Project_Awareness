package com.awareness.app.ui.academy;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;

import com.airbnb.lottie.LottieAnimationView;
import com.awareness.app.R;
import com.awareness.app.databinding.FragmentLessonDetailBinding;
import com.awareness.app.model.Lesson;
import com.awareness.app.utils.Constants;
import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class LessonDetailFragment extends Fragment {

    private FragmentLessonDetailBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;

    // ExoPlayer instance for video lessons
    // We keep a reference so we can release it when the fragment is destroyed
    private ExoPlayer player;

    // The lesson loaded from Firestore
    private Lesson currentLesson;

    // ── Lifecycle ─────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLessonDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db            = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupToolbar();

        // Retrieve the lessonId that AcademyFragment passed via Navigation
        String lessonId = getArguments() != null
                ? getArguments().getString(Constants.EXTRA_LESSON_ID)
                : null;

        if (lessonId == null) {
            // Safety check — shouldn't happen, but go back if it does
            Navigation.findNavController(view).navigateUp();
            return;
        }

        loadLesson(lessonId);
    }

    // ── Setup ─────────────────────────────────────────────────────

    private void setupToolbar() {
        // Back arrow press → go back to Academy
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    // ── Data Loading ──────────────────────────────────────────────

    /**
     * Fetches the lesson document from Firestore by its ID.
     * Then calls renderLesson() to display it.
     */
    private void loadLesson(String lessonId) {
        showLoading(true);

        db.collection(Constants.COLLECTION_LESSONS)
                .document(lessonId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        showLoading(false);
                        return;
                    }

                    currentLesson = doc.toObject(Lesson.class);
                    if (currentLesson != null) {
                        renderLesson(currentLesson);
                        checkIfAlreadyCompleted(lessonId);
                    }
                })
                .addOnFailureListener(e -> showLoading(false));
    }

    /**
     * Checks Firestore to see if this user already completed this lesson.
     * If yes → show a "Already Completed" button instead of the green one.
     * This prevents users from farming XP by reopening the same lesson.
     *
     * We store completed lessons in: users/{uid}/completedLessons/{lessonId}
     */
    private void checkIfAlreadyCompleted(String lessonId) {
        db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .collection("completedLessons")
                .document(lessonId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        // Already done — show disabled button
                        binding.btnComplete.setText("✓ Already Completed");
                        binding.btnComplete.setEnabled(false);
                        binding.btnComplete.setAlpha(0.6f);
                    } else {
                        // Not yet done — show active green button
                        binding.btnComplete.setEnabled(true);
                        binding.btnComplete.setAlpha(1.0f);
                    }
                    binding.btnComplete.setVisibility(View.VISIBLE);
                });
    }

    // ── Rendering ─────────────────────────────────────────────────

    /**
     * Takes a loaded Lesson object and populates the UI.
     * Switches the media area based on lesson type.
     */
    private void renderLesson(Lesson lesson) {
        showLoading(false);

        // Set toolbar title
        binding.toolbar.setTitle(lesson.getTitle());

        // Fill in text fields
        binding.tvTitle.setText(lesson.getTitle());
        binding.tvCategory.setText(lesson.getCategory());
        binding.tvXpReward.setText("⭐ +" + lesson.getXpReward() + " XP");
        binding.tvContent.setText(lesson.getContent());

        // Show the content container
        binding.layoutContent.setVisibility(View.VISIBLE);

        // ── Switch content area based on lesson type ──────────────
        switch (lesson.getType()) {

            case Constants.LESSON_TYPE_VIDEO:
                renderVideo(lesson.getMediaUrl());
                break;

            case Constants.LESSON_TYPE_IMAGE:
                renderImage(lesson.getMediaUrl());
                break;

            case Constants.LESSON_TYPE_TEXT:
            default:
                // Text-only: no media area needed, just body text
                binding.playerView.setVisibility(View.GONE);
                binding.imgLesson.setVisibility(View.GONE);
                break;
        }

        // Show the complete button (checkIfAlreadyCompleted sets its state)
        setupCompleteButton(lesson);
    }

    /**
     * Sets up the ExoPlayer for video lessons.
     * ExoPlayer handles: buffering, playback controls, fullscreen toggle.
     */
    private void renderVideo(String videoUrl) {
        binding.imgLesson.setVisibility(View.GONE);
        binding.playerView.setVisibility(View.VISIBLE);

        // Create a new ExoPlayer instance
        player = new ExoPlayer.Builder(requireContext()).build();

        // Attach the player to the PlayerView in the layout
        binding.playerView.setPlayer(player);

        // Load the video URL
        MediaItem mediaItem = MediaItem.fromUri(videoUrl);
        player.setMediaItem(mediaItem);

        // Prepare buffers the video (doesn't auto-play until user presses play)
        player.prepare();
    }

    /**
     * Loads an image using Glide for image-type lessons.
     */
    private void renderImage(String imageUrl) {
        binding.playerView.setVisibility(View.GONE);
        binding.imgLesson.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_image)
                .error(R.drawable.ic_image)
                .centerCrop()
                .into(binding.imgLesson);
    }

    private void setupCompleteButton(Lesson lesson) {
        binding.btnComplete.setOnClickListener(v -> completeLesson(lesson));
    }

    // ── XP & Completion Logic ─────────────────────────────────────

    /**
     * Called when the user taps "Mark as Complete".
     *
     * Does 3 Firestore writes atomically:
     * 1. Increment user's XP by lesson.getXpReward()
     * 2. Increment user's lessonsCompleted count
     * 3. Create a document in completedLessons sub-collection
     *
     * FieldValue.increment() is an atomic server-side operation —
     * it's safe even if two devices write at the same time.
     */
    private void completeLesson(Lesson lesson) {
        // Disable button immediately to prevent double-taps
        binding.btnComplete.setEnabled(false);
        binding.btnComplete.setText("Saving...");

        DocumentReference userRef = db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId);

        // ── Write 1 & 2: Update XP and lesson count on the user document ──
        userRef.update(
                "xpPoints",         FieldValue.increment(lesson.getXpReward()),
                "lessonsCompleted", FieldValue.increment(1)
        ).addOnSuccessListener(unused -> {

            // ── Write 3: Mark lesson as completed in sub-collection ──
            // Storing the timestamp lets us show "Completed on [date]" later
            userRef.collection("completedLessons")
                    .document(lesson.getLessonId())
                    .set(new java.util.HashMap<String, Object>() {{
                        put("completedAt", FieldValue.serverTimestamp());
                        put("xpEarned",    lesson.getXpReward());
                    }})
                    .addOnSuccessListener(v -> {
                        // ── All writes done: get new XP total and show dialog ──
                        userRef.get().addOnSuccessListener(doc -> {
                            Long newTotal = doc.getLong("xpPoints");
                            checkAndLevelUp(userRef, newTotal);
                            showXpRewardDialog(lesson.getXpReward(),
                                    newTotal != null ? newTotal : 0);
                        });
                    });

        }).addOnFailureListener(e -> {
            // Re-enable on failure so user can retry
            binding.btnComplete.setEnabled(true);
            binding.btnComplete.setText("Mark as Complete  ✓");
        });
    }

    /**
     * Checks if the user has earned enough XP to level up.
     * Formula: level N requires N * 100 XP.
     * e.g. Level 2 = 200 XP, Level 3 = 300 XP, Level 4 = 400 XP
     */
    private void checkAndLevelUp(DocumentReference userRef, Long currentXp) {
        if (currentXp == null) return;

        userRef.get().addOnSuccessListener(doc -> {
            Long currentLevel = doc.getLong("level");
            if (currentLevel == null) currentLevel = 1L;

            long xpThreshold = (currentLevel + 1) * 100L;

            if (currentXp >= xpThreshold) {
                // Level up! Update Firestore
                userRef.update("level", FieldValue.increment(1));
            }
        });
    }

    /**
     * Shows the XP reward dialog with Lottie animation.
     * On "Continue" → navigates back to Academy.
     */
    private void showXpRewardDialog(int xpEarned, long newTotal) {
        Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_xp_reward);

        // Make background transparent so the card has rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(
                    (int) (getResources().getDisplayMetrics().widthPixels * 0.88),
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
        }

        // Prevent dismissing by tapping outside
        dialog.setCancelable(false);

        // Fill in the dialog views
        TextView tvXpEarned = dialog.findViewById(R.id.tvXpEarned);
        TextView tvNewTotal = dialog.findViewById(R.id.tvNewTotal);
        MaterialButton btnContinue = dialog.findViewById(R.id.btnContinue);
        LottieAnimationView lottieAnim = dialog.findViewById(R.id.lottieAnim);

        tvXpEarned.setText("+" + xpEarned + " XP earned!");
        tvNewTotal.setText("Total XP: " + newTotal);

        // Play the Lottie success animation
        lottieAnim.playAnimation();

        btnContinue.setOnClickListener(v -> {
            dialog.dismiss();
            // Go back to Academy
            if (isAdded()) {
                Navigation.findNavController(requireView()).navigateUp();
            }
        });

        dialog.show();
    }

    // ── UI Helpers ────────────────────────────────────────────────

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.layoutContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    // ── Lifecycle: Release ExoPlayer ──────────────────────────────

    /**
     * IMPORTANT: ExoPlayer holds system resources (audio focus, decode buffers).
     * We MUST release it when the fragment is paused/stopped, otherwise
     * it keeps running in the background and drains the battery.
     */
    @Override
    public void onPause() {
        super.onPause();
        if (player != null) {
            player.pause(); // Pause playback when user leaves the screen
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Release the player completely when the fragment is destroyed
        if (player != null) {
            player.release();
            player = null;
        }
        binding = null;
    }
}

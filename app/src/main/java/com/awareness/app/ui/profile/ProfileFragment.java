package com.awareness.app.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.awareness.app.R;
import com.awareness.app.databinding.FragmentProfileBinding;
import com.awareness.app.model.User;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db            = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        loadUserProfile();
        setupClickListeners();
    }

    // ── Data Loading ──────────────────────────────────────────────

    /**
     * Fetches the user document from Firestore and populates all UI fields.
     * Uses a real-time listener so stats update instantly after completing
     * a lesson without needing to refresh.
     */
    private void loadUserProfile() {
        db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .addSnapshotListener((doc, error) -> {
                    if (!isAdded() || doc == null || !doc.exists()) return;

                    User user = doc.toObject(User.class);
                    if (user == null) return;

                    renderProfile(user);
                    updateStreakIfNeeded(user);
                });
    }

    // ── Rendering ─────────────────────────────────────────────────

    private void renderProfile(User user) {
        // Avatar initial
        String name = user.getDisplayName() != null ? user.getDisplayName() : "?";
        binding.tvAvatar.setText(name.substring(0, 1).toUpperCase());
        binding.tvDisplayName.setText(name);

        // Email from Firebase Auth (not stored in Firestore for privacy)
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();
        binding.tvEmail.setText(email != null ? email : "");

        // Level badge
        int level = user.getLevel() > 0 ? user.getLevel() : 1;
        binding.tvLevelBadge.setText("⭐ Level " + level + " Guardian");

        // Stats row
        binding.tvXpStat.setText(String.valueOf(user.getXpPoints()));
        binding.tvStreakStat.setText(user.getStreakCount() + " 🔥");
        binding.tvLessonsStat.setText(String.valueOf(user.getLessonsCompleted()));

        // Region
        binding.tvRegion.setText(
                user.getRegion() != null && !user.getRegion().isEmpty()
                        ? user.getRegion() : "Not set");

        // Level progress bar
        renderLevelProgress(user.getXpPoints(), user.getLevel());
    }

    /**
     * Calculates XP progress toward the next level and updates the bar.
     *
     * Formula:
     *   Level N requires N * 100 XP total to reach.
     *   e.g. Level 2 needs 200 XP, Level 3 needs 300 XP.
     *
     *   currentLevelXp = xpPoints - (level * (level-1) / 2 * 100)
     *   xpForThisLevel = level * 100
     *   progress %     = (currentLevelXp / xpForThisLevel) * 100
     */
    private void renderLevelProgress(int xpPoints, int level) {
        int xpRequiredForNextLevel = (level + 1) * 100;
        int xpRequiredForCurrentLevel = level * 100;

        // XP accumulated since reaching current level
        int xpIntoCurrentLevel = xpPoints - xpRequiredForCurrentLevel;
        int xpNeededThisLevel  = xpRequiredForNextLevel - xpRequiredForCurrentLevel;

        int progressPercent = (int) ((xpIntoCurrentLevel / (float) xpNeededThisLevel) * 100);
        progressPercent = Math.max(0, Math.min(100, progressPercent));

        binding.tvCurrentLevel.setText("Level " + level);
        binding.tvXpToNextLevel.setText(
                xpPoints + " / " + xpRequiredForNextLevel + " XP → Level " + (level + 1));
        binding.progressLevel.setProgress(progressPercent, true);
    }

    // ── Streak Logic ──────────────────────────────────────────────

    /**
     * Updates the daily streak when the profile is loaded.
     *
     * Rules:
     *  - Today = same date as lastActiveDate → streak unchanged (already active today)
     *  - Yesterday = lastActiveDate          → increment streak
     *  - Older than yesterday                → reset streak to 1 (broken)
     *
     * Uses "yyyy-MM-dd" string comparison (no timezone issues).
     */
    private void updateStreakIfNeeded(User user) {
        String today     = getTodayDateString();
        String lastActive = user.getLastActiveDate();

        if (today.equals(lastActive)) return; // Already counted today

        String yesterday = getYesterdayDateString();

        // Determine new streak value
        int newStreak;
        if (yesterday.equals(lastActive)) {
            newStreak = user.getStreakCount() + 1; // Consecutive day — extend streak
        } else {
            newStreak = 1; // Gap in activity — reset streak
        }

        // Write updated streak + lastActiveDate to Firestore
        db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .update(
                        "streakCount",     newStreak,
                        "lastActiveDate",  today
                );
    }

    private String getTodayDateString() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getYesterdayDateString() {
        long oneDayMs = 24 * 60 * 60 * 1000L;
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                .format(new Date(System.currentTimeMillis() - oneDayMs));
    }

    // ── Click Listeners ───────────────────────────────────────────

    private void setupClickListeners() {
        // Leaderboard card
        binding.cardLeaderboard.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.leaderboardFragment));

        // Logout
        binding.rowLogout.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).logout();
            }
        });

        // About — simple Toast for now, can expand to a DialogFragment later
        binding.rowAbout.setOnClickListener(v ->
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setTitle("About Awareness")
                        .setMessage("Awareness v1.0\n\nA gamified anti-scam education app " +
                                "for the Vietnamese community.\n\nBuilt with ❤️ to keep you safe online.")
                        .setPositiveButton("OK", null)
                        .show());
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

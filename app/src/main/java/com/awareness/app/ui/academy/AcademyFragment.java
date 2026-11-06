package com.awareness.app.ui.academy;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.R;
import com.awareness.app.adapter.LessonAdapter;
import com.awareness.app.databinding.FragmentAcademyBinding;
import com.awareness.app.model.Lesson;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class AcademyFragment extends Fragment implements LessonAdapter.OnLessonClickListener {

    private FragmentAcademyBinding binding;
    private FirebaseFirestore db;
    private LessonAdapter adapter;

    // Holds ALL lessons fetched from Firestore (unfiltered master list)
    private List<Lesson> allLessons = new ArrayList<>();

    // Currently selected category filter (null = "All")
    private String selectedCategory = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAcademyBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();

        setupRecyclerView();
        setupCategoryChips();
        setupScamLabBanner();
        setupQuizBanner();
        loadUserStats();
        loadLessons();
    }

    // ── Setup ────────────────────────────────────────────────────

    private void setupRecyclerView() {
        adapter = new LessonAdapter(requireContext(), this);
        // LinearLayoutManager = a vertical scrolling list
        binding.rvLessons.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLessons.setAdapter(adapter);
    }

    private void setupCategoryChips() {
        // When a chip is selected, update the filter and re-display lessons
        binding.chipGroupCategory.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;

            int checkedId = checkedIds.get(0);

            if      (checkedId == R.id.chipAll)        selectedCategory = null;
            else if (checkedId == R.id.chipPhishing)   selectedCategory = Constants.CATEGORY_PHISHING;
            else if (checkedId == R.id.chipVoip)       selectedCategory = Constants.CATEGORY_VOIP;
            else if (checkedId == R.id.chipDeepfake)   selectedCategory = Constants.CATEGORY_DEEPFAKE;
            else if (checkedId == R.id.chipRomance)    selectedCategory = Constants.CATEGORY_ROMANCE;
            else if (checkedId == R.id.chipInvestment) selectedCategory = Constants.CATEGORY_INVESTMENT;

            // Apply filter to the already-loaded lessons (no new Firestore call needed)
            applyFilter();
        });
    }

    private void setupScamLabBanner() {
        binding.cardScamLab.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_academy_to_scamLab));

        binding.btnScamLab.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_academy_to_scamLab));
    }

    private void setupQuizBanner() {
        binding.cardQuiz.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_academy_to_quiz));

        binding.btnQuiz.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_academy_to_quiz));
    }

    // ── Data Loading ─────────────────────────────────────────────

    /**
     * Fetches the current user's streak and XP from Firestore
     * and updates the header badges.
     */
    private void loadUserStats() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Long streak = doc.getLong("streakCount");
                        Long xp     = doc.getLong("xpPoints");

                        binding.tvStreakCount.setText(streak != null ? String.valueOf(streak) : "0");
                        binding.tvXpCount.setText((xp != null ? String.valueOf(xp) : "0") + " XP");
                    }
                });
    }

    /**
     * Fetches all published lessons from Firestore, ordered by orderIndex.
     * Uses a real-time listener (addSnapshotListener) so the list
     * updates automatically if an admin publishes a new lesson.
     */
    private void loadLessons() {
        showLoading(true);

        db.collection(Constants.COLLECTION_LESSONS)
                .whereEqualTo("published", true)
                .orderBy("orderIndex", Query.Direction.ASCENDING)
                .addSnapshotListener((querySnapshot, error) -> {

                    showLoading(false);

                    if (error != null || querySnapshot == null) return;

                    // Convert Firestore documents → Lesson objects
                    allLessons = querySnapshot.toObjects(Lesson.class);

                    // Apply the current filter (or show all if none selected)
                    applyFilter();
                });
    }

    /**
     * Filters allLessons by the selected category and updates the adapter.
     * No extra Firestore call — we filter the in-memory list.
     */
    private void applyFilter() {
        List<Lesson> filtered = new ArrayList<>();

        for (Lesson lesson : allLessons) {
            if (selectedCategory == null || selectedCategory.equals(lesson.getCategory())) {
                filtered.add(lesson);
            }
        }

        adapter.setLessons(filtered);

        // Show empty state if nothing matched the filter
        binding.tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── Lesson Click (from LessonAdapter.OnLessonClickListener) ──

    @Override
    public void onLessonClick(Lesson lesson) {
        // Pass the lessonId to LessonDetailFragment via Navigation Safe Args
        Bundle args = new Bundle();
        args.putString(Constants.EXTRA_LESSON_ID, lesson.getLessonId());

        Navigation.findNavController(requireView())
                .navigate(R.id.action_academy_to_lessonDetail, args);
    }

    // ── UI Helpers ───────────────────────────────────────────────

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.rvLessons.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    // ── Cleanup ──────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leaks — always null binding in onDestroyView
    }
}

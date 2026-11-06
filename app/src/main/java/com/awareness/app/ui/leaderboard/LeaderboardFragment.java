package com.awareness.app.ui.leaderboard;

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
import com.awareness.app.adapter.LeaderboardAdapter;
import com.awareness.app.databinding.FragmentLeaderboardBinding;
import com.awareness.app.model.LeaderboardEntry;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LeaderboardFragment extends Fragment {

    private FragmentLeaderboardBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;
    private LeaderboardAdapter adapter;

    // ── Lifecycle ─────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLeaderboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db            = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        setupToolbar();
        setupRecyclerView();
        syncCurrentUserToLeaderboard(); // Write our latest stats before fetching
        loadLeaderboard();
    }

    // ── Setup ─────────────────────────────────────────────────────

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupRecyclerView() {
        adapter = new LeaderboardAdapter(requireContext(), currentUserId);
        binding.rvLeaderboard.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvLeaderboard.setAdapter(adapter);
    }

    // ── Sync Current User ─────────────────────────────────────────

    /**
     * Before loading the leaderboard, pushes the current user's latest
     * XP / level / streak into the leaderboard collection.
     *
     * Why do this here?
     * Firestore doesn't support cross-collection queries, so we maintain
     * a denormalised "leaderboard" collection that mirrors just the fields
     * needed for ranking. We update it every time the user opens this screen.
     *
     * In a production app you would use a Cloud Function triggered by
     * writes to the users collection — but for this project this is simpler.
     */
    private void syncCurrentUserToLeaderboard() {
        db.collection(Constants.COLLECTION_USERS)
                .document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("userId",      currentUserId);
                    entry.put("displayName", doc.getString("displayName"));
                    entry.put("xpPoints",    doc.getLong("xpPoints") != null
                            ? doc.getLong("xpPoints").intValue() : 0);
                    entry.put("level",       doc.getLong("level") != null
                            ? doc.getLong("level").intValue() : 1);
                    entry.put("region",      doc.getString("region"));
                    entry.put("streakCount", doc.getLong("streakCount") != null
                            ? doc.getLong("streakCount").intValue() : 0);
                    entry.put("updatedAt",   FieldValue.serverTimestamp());

                    // set() with merge=true: creates if not exists, updates if it does
                    db.collection(Constants.COLLECTION_LEADERBOARD)
                            .document(currentUserId)
                            .set(entry);
                });
    }

    // ── Data Loading ──────────────────────────────────────────────

    /**
     * Fetches top 50 entries from the leaderboard collection,
     * ordered by xpPoints descending.
     *
     * After fetching:
     * 1. Sort (Firestore already returns them sorted)
     * 2. Assign rank numbers 1–N
     * 3. Render top 3 into the podium
     * 4. Find the current user's position → "Your Rank" card
     * 5. Pass positions 4–N to the RecyclerView adapter
     */
    private void loadLeaderboard() {
        showLoading(true);

        db.collection(Constants.COLLECTION_LEADERBOARD)
                .orderBy("xpPoints", Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    // Convert to LeaderboardEntry list and assign ranks
                    List<LeaderboardEntry> entries = new ArrayList<>();
                    List<LeaderboardEntry> rawList =
                            querySnapshot.toObjects(LeaderboardEntry.class);

                    for (int i = 0; i < rawList.size(); i++) {
                        LeaderboardEntry e = rawList.get(i);
                        e.setRank(i + 1);
                        entries.add(e);
                    }

                    showLoading(false);
                    renderPodium(entries);
                    renderYourRankCard(entries);

                    // RecyclerView shows rank 4 onwards (podium already shows 1-3)
                    List<LeaderboardEntry> listEntries = entries.size() > 3
                            ? entries.subList(3, entries.size())
                            : new ArrayList<>();
                    adapter.setEntries(listEntries);
                })
                .addOnFailureListener(e -> showLoading(false));
    }

    // ── Podium Rendering ──────────────────────────────────────────

    /**
     * Fills the podium with the top 3 entries.
     * Layout order: 2nd (left) | 1st (centre) | 3rd (right)
     */
    private void renderPodium(List<LeaderboardEntry> entries) {
        if (entries.size() >= 1) fillPodiumSlot(entries.get(0), 1);
        if (entries.size() >= 2) fillPodiumSlot(entries.get(1), 2);
        if (entries.size() >= 3) fillPodiumSlot(entries.get(2), 3);
    }

    /**
     * Fills one podium slot (1st, 2nd, or 3rd) with the entry's data.
     * @param entry The leaderboard entry to display
     * @param place 1, 2, or 3
     */
    private void fillPodiumSlot(LeaderboardEntry entry, int place) {
        android.widget.TextView tvAvatar, tvName, tvXp;

        switch (place) {
            case 1:
                tvAvatar = binding.tvAvatar1;
                tvName   = binding.tvName1;
                tvXp     = binding.tvXp1;
                break;
            case 2:
                tvAvatar = binding.tvAvatar2;
                tvName   = binding.tvName2;
                tvXp     = binding.tvXp2;
                break;
            default: // 3
                tvAvatar = binding.tvAvatar3;
                tvName   = binding.tvName3;
                tvXp     = binding.tvXp3;
                break;
        }

        tvAvatar.setText(entry.getAvatarLetter());
        tvName.setText(entry.getDisplayName() != null
                ? entry.getDisplayName() : "Anonymous");
        tvXp.setText(entry.getXpPoints() + " XP");

        // If this podium slot is the current user, add a subtle ring
        // by changing the avatar background tint
        if (currentUserId.equals(entry.getUserId())) {
            tvName.setText(tvName.getText() + " 👈");
        }
    }

    // ── Your Rank Card ────────────────────────────────────────────

    /**
     * Searches the ranked list for the current user and fills the
     * "Your Rank" card that is always visible at the top of the list.
     * If the user is in the top 3, the card still shows their rank number.
     */
    private void renderYourRankCard(List<LeaderboardEntry> entries) {
        LeaderboardEntry myEntry = null;

        for (LeaderboardEntry e : entries) {
            if (currentUserId.equals(e.getUserId())) {
                myEntry = e;
                break;
            }
        }

        if (myEntry == null) {
            // User not yet in leaderboard — show a placeholder
            binding.tvYourRankNumber.setText("#—");
            binding.tvYourName.setText("You");
            binding.tvYourXp.setText("Keep earning XP to rank up!");
            binding.tvYourLevel.setText("Lv.1");
            binding.tvYourAvatar.setText("?");
            return;
        }

        binding.tvYourRankNumber.setText("#" + myEntry.getRank());
        binding.tvYourAvatar.setText(myEntry.getAvatarLetter());
        binding.tvYourName.setText(myEntry.getDisplayName() != null
                ? myEntry.getDisplayName() : "You");
        binding.tvYourXp.setText(myEntry.getXpPoints() + " XP");
        binding.tvYourLevel.setText("Lv." + myEntry.getLevel());

        // Scroll the RecyclerView to the user's position if they're
        // outside the visible window (rank > ~8)
        int listPosition = myEntry.getRank() - 4; // offset because podium shows 1-3
        if (listPosition > 5 && listPosition < adapter.getItemCount()) {
            binding.rvLeaderboard.post(() ->
                    binding.rvLeaderboard.smoothScrollToPosition(listPosition));
        }
    }

    // ── UI Helpers ────────────────────────────────────────────────

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.layoutContent.setVisibility(isLoading ? View.GONE : View.VISIBLE);
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

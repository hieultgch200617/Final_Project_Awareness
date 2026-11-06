package com.awareness.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.awareness.app.databinding.FragmentDashboardBinding;
import com.awareness.app.utils.Constants;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class DashboardFragment extends Fragment {

    private FragmentDashboardBinding binding;
    private FirebaseFirestore db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        db      = FirebaseFirestore.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadStats();
        loadRecentScans();
        loadScamTypeBreakdown();
    }

    // ── Stats Cards ───────────────────────────────────────────────

    private void loadStats() {
        // Total users
        db.collection(Constants.COLLECTION_USERS)
                .get()
                .addOnSuccessListener(snap ->
                        binding.tvTotalUsers.setText(String.valueOf(snap.size())));

        // Total scans
        db.collection(Constants.COLLECTION_SCANS)
                .get()
                .addOnSuccessListener(snap ->
                        binding.tvTotalScans.setText(String.valueOf(snap.size())));

        // Total reports
        db.collection(Constants.COLLECTION_REPORTS)
                .get()
                .addOnSuccessListener(snap ->
                        binding.tvTotalReports.setText(String.valueOf(snap.size())));

        // Malicious scans count
        db.collection(Constants.COLLECTION_SCANS)
                .whereEqualTo("rating", Constants.RATING_MALICIOUS)
                .get()
                .addOnSuccessListener(snap ->
                        binding.tvMaliciousCount.setText(String.valueOf(snap.size())));
    }

    // ── Recent Scan Logs ──────────────────────────────────────────

    /**
     * Loads the 20 most recent scans and populates the scan log list.
     */
    private void loadRecentScans() {
        db.collection(Constants.COLLECTION_SCANS)
                .orderBy("scannedAt", Query.Direction.DESCENDING)
                .limit(20)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (!isAdded() || querySnapshot == null) return;

                    // Build a simple text log of scans
                    StringBuilder log = new StringBuilder();
                    for (var doc : querySnapshot.getDocuments()) {
                        String type   = doc.getString("type");
                        String query  = doc.getString("query");
                        String rating = doc.getString("rating");
                        String emoji  = Constants.RATING_MALICIOUS.equals(rating) ? "🚨"
                                : Constants.RATING_SUSPICIOUS.equals(rating) ? "⚠️" : "✅";
                        log.append(emoji)
                                .append(" [").append(type != null ? type.toUpperCase() : "?").append("] ")
                                .append(query)
                                .append("\n");
                    }

                    if (isAdded()) {
                        binding.tvScanLog.setText(
                                log.length() > 0 ? log.toString() : "No scans yet.");
                    }
                });
    }

    // ── Scam Type Breakdown ───────────────────────────────────────

    /**
     * Counts reports grouped by scam type and displays as a simple list.
     * In a future iteration this could use MPAndroidChart for a pie chart.
     */
    private void loadScamTypeBreakdown() {
        db.collection(Constants.COLLECTION_REPORTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!isAdded()) return;

                    Map<String, Integer> counts = new HashMap<>();
                    for (var doc : querySnapshot.getDocuments()) {
                        String type = doc.getString("scamType");
                        if (type != null) {
                            counts.put(type, counts.getOrDefault(type, 0) + 1);
                        }
                    }

                    StringBuilder breakdown = new StringBuilder();
                    counts.entrySet().stream()
                            .sorted((a, b) -> b.getValue() - a.getValue())
                            .forEach(e -> breakdown
                                    .append("• ").append(e.getKey())
                                    .append(": ").append(e.getValue())
                                    .append(" reports\n"));

                    binding.tvScamBreakdown.setText(
                            breakdown.length() > 0
                                    ? breakdown.toString()
                                    : "No reports yet.");
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

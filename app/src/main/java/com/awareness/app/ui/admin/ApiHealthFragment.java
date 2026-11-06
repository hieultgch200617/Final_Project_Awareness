package com.awareness.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.awareness.app.R;
import com.awareness.app.databinding.FragmentApiHealthBinding;
import com.awareness.app.repository.GuardianRepository;
import com.awareness.app.utils.Constants;
import com.google.firebase.firestore.AggregateQuery;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.DateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Admin → API Health.
 *
 *  1. Safe Browsing: a real probe (harmless URL) → HTTP status + latency.
 *  2. Phone checker: asks the staff-only "apiHealth" Cloud Function whether the Telesign
 *     secrets are set and how many billable checks happened today. We never make a
 *     paid Telesign call just to "ping".
 *  3. Usage: server-side count() aggregations on scan_logs (cheap: 1 read per 1000 docs).
 *     Only single-field filters are used, so no composite indexes are needed.
 */
public class ApiHealthFragment extends Fragment {

    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private FragmentApiHealthBinding binding;
    private FirebaseFirestore db;
    private GuardianRepository repository;
    private int pendingProbes = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentApiHealthBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();
        repository = GuardianRepository.getInstance();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnRefresh.setOnClickListener(v -> runChecks());
        runChecks();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Orchestration ─────────────────────────────────────────────

    private void runChecks() {
        pendingProbes = 2;
        binding.progressHealth.setVisibility(View.VISIBLE);
        binding.btnRefresh.setEnabled(false);

        setStatus(binding.tvSbStatus, "● Checking…", R.color.text_secondary);
        binding.tvSbDetail.setText("");
        setStatus(binding.tvPhoneStatus, "● Checking…", R.color.text_secondary);
        binding.tvPhoneDetail.setText("");
        binding.tvTelesignToday.setText("");

        probeSafeBrowsing();
        probeBackend();
        loadUsage();
    }

    private void probeFinished() {
        if (binding == null) return;
        pendingProbes--;
        if (pendingProbes <= 0) {
            binding.progressHealth.setVisibility(View.INVISIBLE);
            binding.btnRefresh.setEnabled(true);
            binding.tvLastChecked.setText("Last checked "
                    + DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
        }
    }

    // ── 1. Safe Browsing ──────────────────────────────────────────

    private void probeSafeBrowsing() {
        repository.pingSafeBrowsing((ok, code, latencyMs, error) -> {
            if (binding == null) return;

            String timing = latencyMs + " ms";
            if (ok) {
                setStatus(binding.tvSbStatus, "● Operational", R.color.rating_safe);
                binding.tvSbDetail.setText("HTTP " + code + " · " + timing);
            } else if (code == 0) {
                setStatus(binding.tvSbStatus, "● Unreachable", R.color.rating_malicious);
                binding.tvSbDetail.setText(error != null ? error : "No response");
            } else if (code == 400) {
                setStatus(binding.tvSbStatus, "● API key missing or invalid", R.color.rating_malicious);
                binding.tvSbDetail.setText("HTTP 400 · Is SAFE_BROWSING_API_KEY set in local.properties?");
            } else if (code == 403) {
                setStatus(binding.tvSbStatus, "● API key rejected", R.color.rating_malicious);
                binding.tvSbDetail.setText("HTTP 403 · Key restrictions don't match this app, "
                        + "or the Safe Browsing API isn't enabled in Google Cloud.");
            } else if (code == 429) {
                setStatus(binding.tvSbStatus, "● Quota exceeded", R.color.rating_suspicious);
                binding.tvSbDetail.setText("HTTP 429 · Daily quota used up. Check Google Cloud → Quotas.");
            } else {
                setStatus(binding.tvSbStatus, "● Error", R.color.rating_malicious);
                binding.tvSbDetail.setText("HTTP " + code + " · " + timing);
            }
            probeFinished();
        });
    }

    // ── 2. Phone checker backend ──────────────────────────────────

    private void probeBackend() {
        repository.fetchBackendHealth(new GuardianRepository.BackendHealthCallback() {
            @Override
            public void onResult(Map<String, Object> data) {
                if (binding == null) return;

                boolean configured = Boolean.TRUE.equals(data.get("telesignConfigured"));
                long today = data.get("phoneChecksToday") instanceof Number
                        ? ((Number) data.get("phoneChecksToday")).longValue() : 0;
                long perUser = data.get("dailyLimitPerUser") instanceof Number
                        ? ((Number) data.get("dailyLimitPerUser")).longValue() : 0;

                if (configured) {
                    setStatus(binding.tvPhoneStatus, "● Ready", R.color.rating_safe);
                    binding.tvPhoneDetail.setText("Cloud Function is deployed and Telesign credentials "
                            + "are set. They're only proven valid by a real phone check.");
                } else {
                    setStatus(binding.tvPhoneStatus, "● Credentials not set", R.color.rating_malicious);
                    binding.tvPhoneDetail.setText("Run: firebase functions:secrets:set TELESIGN_CUSTOMER_ID "
                            + "and TELESIGN_API_KEY, then redeploy.");
                }
                binding.tvTelesignToday.setText("Billable phone checks today: " + today
                        + "  (limit " + perUser + " per user per day)");
                probeFinished();
            }

            @Override
            public void onError(String message) {
                if (binding == null) return;
                setStatus(binding.tvPhoneStatus, "● Unavailable", R.color.rating_malicious);
                binding.tvPhoneDetail.setText(message);
                probeFinished();
            }
        });
    }

    // ── 3. Usage counters ─────────────────────────────────────────

    private void loadUsage() {
        CollectionReference scans = db.collection(Constants.COLLECTION_SCANS);
        long now = System.currentTimeMillis();

        count(scans.whereGreaterThan("scannedAt", new Date(now - DAY_MS)).count(),
                binding.tvScans24h, "Last 24 hours: ");
        count(scans.whereGreaterThan("scannedAt", new Date(now - 7 * DAY_MS)).count(),
                binding.tvScans7d, "Last 7 days: ");
        count(scans.whereEqualTo("type", "url").count(),
                binding.tvScansUrl, "All-time URL scans: ");
        count(scans.whereEqualTo("type", "phone").count(),
                binding.tvScansPhone, "All-time phone checks: ");
        count(scans.whereEqualTo("rating", Constants.RATING_MALICIOUS).count(),
                binding.tvScansMalicious, "Rated malicious: ");
    }

    private void count(AggregateQuery query, TextView target, String label) {
        query.get(AggregateSource.SERVER)
                .addOnSuccessListener(r -> {
                    if (binding != null) target.setText(label + r.getCount());
                })
                .addOnFailureListener(e -> {
                    if (binding != null) target.setText(label + "unavailable");
                });
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void setStatus(TextView view, String text, int colorRes) {
        view.setText(text);
        view.setTextColor(ContextCompat.getColor(requireContext(), colorRes));
    }
}

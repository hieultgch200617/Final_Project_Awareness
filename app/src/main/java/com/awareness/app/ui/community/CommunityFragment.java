package com.awareness.app.ui.community;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.R;
import com.awareness.app.adapter.ReportAdapter;
import com.awareness.app.databinding.FragmentCommunityBinding;
import com.awareness.app.model.ScamReport;
import com.awareness.app.utils.Constants;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import com.awareness.app.databinding.BottomsheetReportBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommunityFragment extends Fragment implements OnMapReadyCallback {

    private FragmentCommunityBinding binding;
    private FirebaseFirestore db;
    private String currentUserId;
    private GoogleMap googleMap;
    private ReportAdapter reportAdapter;

    private static final String[] SCAM_TYPES = {
            "Phishing", "VoIP / Caller ID", "Deepfakes",
            "Romance Scams", "Investment Fraud", "Other"
    };

    // Vietnam center coordinates for initial map position
    private static final LatLng VIETNAM_CENTER = new LatLng(16.0, 106.0);

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding       = FragmentCommunityBinding.inflate(inflater, container, false);
        db            = FirebaseFirestore.getInstance();
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupMap();
        setupRecyclerView();
        loadReports();
        setupFab();
    }

    // ── Map Setup ─────────────────────────────────────────────────

    /**
     * Embeds a SupportMapFragment into the mapContainer FrameLayout.
     * When the map is ready, onMapReady() is called automatically.
     */
    private void setupMap() {
        SupportMapFragment mapFragment = SupportMapFragment.newInstance();
        getChildFragmentManager()
                .beginTransaction()
                .replace(R.id.mapContainer, mapFragment)
                .commit();
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // Zoom to Vietnam on load
        googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(VIETNAM_CENTER, 5.5f));

        // Disable some controls to keep UI clean
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // Add pins for all reports already loaded
        loadMapPins();
    }

    /**
     * Queries Firestore for all reports that have lat/lng and adds map pins.
     * Pin color: red = high-risk scam types, yellow = others.
     */
    private void loadMapPins() {
        db.collection(Constants.COLLECTION_REPORTS)
                .limit(100)    // Cap at 100 pins to avoid cluttering the map
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (googleMap == null) return;

                    for (ScamReport report : querySnapshot.toObjects(ScamReport.class)) {
                        if (report.getLatitude() == 0 && report.getLongitude() == 0) continue;

                        LatLng position = new LatLng(
                                report.getLatitude(), report.getLongitude());

                        float pinColor = isHighRiskType(report.getScamType())
                                ? BitmapDescriptorFactory.HUE_RED
                                : BitmapDescriptorFactory.HUE_YELLOW;

                        googleMap.addMarker(new MarkerOptions()
                                .position(position)
                                .title(report.getScamType())
                                .snippet(report.getRegion())
                                .icon(BitmapDescriptorFactory.defaultMarker(pinColor)));
                    }
                });
    }

    private boolean isHighRiskType(String type) {
        return "Phishing".equals(type) ||
                "VoIP / Caller ID".equals(type) ||
                "Investment Fraud".equals(type);
    }

    // ── Report Feed ───────────────────────────────────────────────

    private void setupRecyclerView() {
        reportAdapter = new ReportAdapter(requireContext(), db, currentUserId);
        binding.rvReports.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvReports.setAdapter(reportAdapter);
    }

    /**
     * Real-time listener on the reports collection.
     * Orders by reportedAt descending (newest first), limited to 50.
     */
    private void loadReports() {
        binding.progressBar.setVisibility(View.VISIBLE);

        db.collection(Constants.COLLECTION_REPORTS)
                .orderBy("reportedAt", Query.Direction.DESCENDING)
                .limit(50)
                .addSnapshotListener((querySnapshot, error) -> {
                    if (!isAdded()) return;

                    binding.progressBar.setVisibility(View.GONE);

                    if (error != null || querySnapshot == null) return;

                    List<ScamReport> reports = querySnapshot.toObjects(ScamReport.class);
                    reportAdapter.setReports(reports);

                    binding.tvEmpty.setVisibility(reports.isEmpty() ? View.VISIBLE : View.GONE);
                    binding.rvReports.setVisibility(reports.isEmpty() ? View.GONE : View.VISIBLE);
                });
    }

    // ── FAB: Submit Report ────────────────────────────────────────

    private void setupFab() {
        binding.fabReport.setOnClickListener(v -> showReportBottomSheet());
    }

    /**
     * Opens a Material BottomSheetDialog for submitting a new scam report.
     */
    private void showReportBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        BottomsheetReportBinding sheetBinding =
                BottomsheetReportBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        // Populate scam type dropdown
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                SCAM_TYPES
        );
        sheetBinding.spinnerScamType.setAdapter(typeAdapter);

        // Populate region dropdown (same list as RegisterActivity)
        ArrayAdapter<String> regionAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                getResources().getStringArray(R.array.vietnam_regions)
        );
        sheetBinding.spinnerRegion.setAdapter(regionAdapter);

        // Submit button
        sheetBinding.btnSubmitReport.setOnClickListener(v -> {
            String scamType   = sheetBinding.spinnerScamType.getText().toString().trim();
            String region     = sheetBinding.spinnerRegion.getText().toString().trim();
            String description = sheetBinding.etDescription.getText() != null
                    ? sheetBinding.etDescription.getText().toString().trim() : "";

            if (TextUtils.isEmpty(scamType)) {
                sheetBinding.tilScamType.setError("Please select a scam type");
                return;
            }
            if (TextUtils.isEmpty(region)) {
                sheetBinding.tilRegion.setError("Please select your region");
                return;
            }
            if (TextUtils.isEmpty(description) || description.length() < 10) {
                sheetBinding.tilDescription.setError("Please describe the scam (min 10 characters)");
                return;
            }

            submitReport(scamType, region, description, dialog);
        });

        dialog.show();
    }

    /**
     * Writes a new ScamReport to Firestore.
     * Lat/lng defaults to the approximate center of the selected region.
     */
    private void submitReport(String scamType, String region,
                              String description, BottomSheetDialog dialog) {
        Map<String, Object> report = new HashMap<>();
        report.put("scamType",    scamType);
        report.put("region",      region);
        report.put("description", description);
        report.put("reportedBy",  currentUserId);
        report.put("upvotes",     0);
        report.put("isVerified",  false);
        report.put("reportedAt",  FieldValue.serverTimestamp());

        // Use approximate center coordinates for the region
        LatLng coords = getRegionCoordinates(region);
        report.put("latitude",  coords.latitude);
        report.put("longitude", coords.longitude);

        db.collection(Constants.COLLECTION_REPORTS)
                .add(report)
                .addOnSuccessListener(ref -> {
                    dialog.dismiss();
                    Toast.makeText(requireContext(),
                            "Report submitted! Thank you for protecting the community. 🛡️",
                            Toast.LENGTH_LONG).show();
                    // Map pin will appear via the real-time listener refreshing
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to submit. Please try again.",
                                Toast.LENGTH_SHORT).show());
    }

    /**
     * Returns approximate center LatLng for major Vietnamese regions.
     * Used for placing the map pin when GPS is not requested.
     */
    private LatLng getRegionCoordinates(String region) {
        switch (region) {
            case "Hà Nội":            return new LatLng(21.0285, 105.8542);
            case "Hồ Chí Minh":       return new LatLng(10.8231, 106.6297);
            case "Đà Nẵng":           return new LatLng(16.0471, 108.2068);
            case "Hải Phòng":         return new LatLng(20.8449, 106.6881);
            case "Cần Thơ":           return new LatLng(10.0452, 105.7469);
            case "Huế":               return new LatLng(16.4637, 107.5909);
            case "Nha Trang":         return new LatLng(12.2388, 109.1967);
            default:                  return VIETNAM_CENTER;
        }
    }

    // ── Cleanup ───────────────────────────────────────────────────

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

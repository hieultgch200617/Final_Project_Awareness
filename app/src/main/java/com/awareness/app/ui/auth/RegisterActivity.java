package com.awareness.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityRegisterBinding;
import com.awareness.app.model.User;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // List of Vietnam regions for the dropdown spinner
    private static final String[] VIETNAM_REGIONS = {
            "Hà Nội", "Hồ Chí Minh", "Đà Nẵng", "Hải Phòng", "Cần Thơ",
            "An Giang", "Bà Rịa - Vũng Tàu", "Bắc Giang", "Bắc Kạn", "Bạc Liêu",
            "Bắc Ninh", "Bến Tre", "Bình Định", "Bình Dương", "Bình Phước",
            "Bình Thuận", "Cà Mau", "Cao Bằng", "Đắk Lắk", "Đắk Nông",
            "Điện Biên", "Đồng Nai", "Đồng Tháp", "Gia Lai", "Hà Giang",
            "Hà Nam", "Hà Tĩnh", "Hải Dương", "Hậu Giang", "Hòa Bình",
            "Hưng Yên", "Khánh Hòa", "Kiên Giang", "Kon Tum", "Lai Châu",
            "Lâm Đồng", "Lạng Sơn", "Lào Cai", "Long An", "Nam Định",
            "Nghệ An", "Ninh Bình", "Ninh Thuận", "Phú Thọ", "Phú Yên",
            "Quảng Bình", "Quảng Nam", "Quảng Ngãi", "Quảng Ninh", "Quảng Trị",
            "Sóc Trăng", "Sơn La", "Tây Ninh", "Thái Bình", "Thái Nguyên",
            "Thanh Hóa", "Thừa Thiên Huế", "Tiền Giang", "Trà Vinh", "Tuyên Quang",
            "Vĩnh Long", "Vĩnh Phúc", "Yên Bái"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        setupRegionDropdown();
        setupLoginLink();
        setupClickListeners();
    }

    /**
     * Populates the region dropdown with Vietnamese provinces.
     */
    private void setupRegionDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                VIETNAM_REGIONS
        );
        binding.spinnerRegion.setAdapter(adapter);
    }

    private void setupLoginLink() {
        String fullText   = "Already have an account? Sign In";
        String linkText   = "Sign In";
        int    startIndex = fullText.indexOf(linkText);
        int    endIndex   = startIndex + linkText.length();

        SpannableString spannable = new SpannableString(fullText);
        spannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_blue)),
                startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) { finish(); } // Just go back to Login
        }, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvLoginLink.setText(spannable);
        binding.tvLoginLink.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setupClickListeners() {
        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnRegister.setOnClickListener(v -> attemptRegister());
    }

    private void attemptRegister() {
        String name            = binding.etName.getText().toString().trim();
        String email           = binding.etEmail.getText().toString().trim();
        String region          = binding.spinnerRegion.getText().toString().trim();
        String password        = binding.etPassword.getText().toString().trim();
        String confirmPassword = binding.etConfirmPassword.getText().toString().trim();

        // ── Validation ───────────────────────────────────────────
        if (TextUtils.isEmpty(name)) {
            binding.tilName.setError("Please enter your name");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Please enter your email");
            return;
        }
        if (TextUtils.isEmpty(region)) {
            binding.tilRegion.setError("Please select your region");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Please enter a password");
            return;
        }
        if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            return;
        }
        if (!password.equals(confirmPassword)) {
            binding.tilConfirmPassword.setError("Passwords do not match");
            return;
        }

        // Clear errors
        binding.tilName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilRegion.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
        hideError();

        setLoading(true);

        // ── Step 1: Create account in Firebase Auth ──────────────
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    // ── Step 2: Create User document in Firestore ─
                    createFirestoreUser(uid, name, email, region);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    showError(getFriendlyErrorMessage(e.getMessage()));
                });
    }

    /**
     * Creates a User document in Firestore after successful Firebase Auth signup.
     * This stores the user's name, role, XP, streak, and region.
     */
    private void createFirestoreUser(String uid, String name, String email, String region) {
        // Create a User object using our model class
        User newUser = new User(uid, name, email);
        newUser.setRegion(region);

        // Write it to Firestore under: users/{uid}
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(unused -> {
                    setLoading(false);
                    // All done — go to the main app
                    Intent intent = new Intent(this, MainActivity.class);
                    // Clear the back stack so user can't go back to Register
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // Auth account was created but Firestore write failed.
                    // Still proceed — we can retry writing user data later.
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                });
    }

    // ── UI Helpers ───────────────────────────────────────────────

    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setEnabled(!isLoading);
        binding.btnRegister.setText(isLoading ? "Creating account..." : "Create Account");
    }

    private void showError(String message) {
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvError.setVisibility(View.GONE);
    }

    private String getFriendlyErrorMessage(String raw) {
        if (raw == null) return "Registration failed. Please try again.";
        if (raw.contains("email address is already in use")) return "This email is already registered.";
        if (raw.contains("badly formatted"))                 return "Please enter a valid email address.";
        if (raw.contains("network"))                         return "Network error. Check your connection.";
        return "Registration failed. Please try again.";
    }
}

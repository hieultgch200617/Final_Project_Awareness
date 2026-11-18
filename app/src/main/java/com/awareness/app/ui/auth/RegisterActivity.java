package com.awareness.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityRegisterBinding;
import com.awareness.app.model.User;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.FirebaseFirestore;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // The server can be slow or unreachable (blocked network). Never leave the user on a spinner.
    private static final long AUTH_TIMEOUT_MS   = 20_000;  // creating the account
    private static final long FIRESTORE_WAIT_MS = 6_000;   // waiting for the server to confirm the profile
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean proceeded = false;                     // we already moved on to the app

    private final Runnable authTimeout = () -> {
        if (isFinishing() || isDestroyed()) return;
        setLoading(false);
        showError("Still no answer from the server. Check your internet connection "
                + "(school or public Wi-Fi can block Firebase), then try again.");
    };

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
        proceeded = false;
        handler.postDelayed(authTimeout, AUTH_TIMEOUT_MS);

        // ── Step 1: Create account in Firebase Auth ──────────────
        mAuth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    handler.removeCallbacks(authTimeout);
                    String uid = authResult.getUser().getUid();

                    // ── Step 2: Create User document in Firestore ─
                    createFirestoreUser(uid, name, email, region);
                })
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(authTimeout);
                    setLoading(false);
                    showError(AuthErrors.describe(e, true));   // says what actually went wrong
                });
    }

    /**
     * Creates a User document in Firestore after successful Firebase Auth signup.
     * This stores the user's name, role, XP, streak, and region.
     *
     * Firestore only reports success once the SERVER has confirmed the write. On a blocked or offline
     * connection that never happens, which used to leave "Creating account..." spinning forever even
     * though the account already existed. The write is kept on the device and sent when the connection
     * works, so after a short wait we continue into the app regardless.
     */
    private void createFirestoreUser(String uid, String name, String email, String region) {
        // Create a User object using our model class
        User newUser = new User(uid, name, email);
        newUser.setRegion(region);

        final Runnable stopWaiting = () -> enterApp(null);
        handler.postDelayed(stopWaiting, FIRESTORE_WAIT_MS);

        // Write it to Firestore under: users/{uid}
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(newUser)
                .addOnSuccessListener(unused -> {
                    handler.removeCallbacks(stopWaiting);
                    enterApp(null);
                })
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(stopWaiting);
                    // Auth account exists but the profile write was rejected. Tell the user why;
                    // signing in later repairs the missing profile automatically.
                    boolean denied = e instanceof FirebaseFirestoreException
                            && ((FirebaseFirestoreException) e).getCode()
                                == FirebaseFirestoreException.Code.PERMISSION_DENIED;
                    enterApp(denied
                            ? "Account created, but Firestore's rules refused to save your profile."
                            : "Account created, but your profile couldn't be saved yet.");
                });
    }

    /** Goes to the main app, once. */
    private void enterApp(String warning) {
        if (proceeded || isFinishing() || isDestroyed()) return;
        proceeded = true;
        setLoading(false);
        if (warning != null) {
            Toast.makeText(getApplicationContext(), warning, Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, MainActivity.class);
        // Clear the back stack so user can't go back to Register
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}

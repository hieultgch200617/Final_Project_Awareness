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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityLoginBinding;
import com.awareness.app.model.User;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.ui.admin.AdminActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    // ViewBinding gives us direct access to every view in activity_login.xml
    // without needing findViewById() — safer and cleaner
    private ActivityLoginBinding binding;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    // If the server never answers we must not leave the user on a spinner forever.
    private static final long AUTH_TIMEOUT_MS      = 20_000;  // sign-in itself
    private static final long FIRESTORE_WAIT_MS    = 8_000;   // reading / repairing the profile
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean routed = false;                          // we already left this screen

    private final Runnable authTimeout = () -> {
        if (isFinishing() || isDestroyed()) return;
        setLoading(false);
        showError("Still no answer from the server. Check your internet connection "
                + "(school or public Wi-Fi can block Firebase), then try again.");
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflate the layout using ViewBinding
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        setupRegisterLink();
        setupClickListeners();
    }

    /**
     * Makes "Don't have an account? Register" a clickable styled link.
     * SpannableString lets us style only part of a text.
     */
    private void setupRegisterLink() {
        String fullText   = "Don't have an account? Register";
        String linkText   = "Register";
        int    startIndex = fullText.indexOf(linkText);
        int    endIndex   = startIndex + linkText.length();

        SpannableString spannable = new SpannableString(fullText);

        // Make "Register" blue
        spannable.setSpan(
                new ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_blue)),
                startIndex, endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // Make "Register" clickable
        spannable.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            }
        }, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        binding.tvRegisterLink.setText(spannable);
        binding.tvRegisterLink.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void setupClickListeners() {
        // Login button
        binding.btnLogin.setOnClickListener(v -> attemptLogin());

        // Forgot password
        binding.tvForgotPassword.setOnClickListener(v -> sendPasswordReset());
    }

    /**
     * Validates inputs, then calls Firebase to sign in.
     */
    private void attemptLogin() {
        // Read what the user typed (trim() removes accidental spaces)
        String email    = binding.etEmail.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();

        // ── Validation ───────────────────────────────────────────
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Please enter your email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError("Please enter your password");
            return;
        }
        if (password.length() < 6) {
            binding.tilPassword.setError("Password must be at least 6 characters");
            return;
        }

        // Clear any previous errors
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        hideError();

        // Show loading state while Firebase processes the login
        setLoading(true);
        routed = false;
        handler.postDelayed(authTimeout, AUTH_TIMEOUT_MS);

        // ── Firebase Sign In ─────────────────────────────────────
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    handler.removeCallbacks(authTimeout);
                    // Login worked — now check the user's role
                    fetchUserRoleAndRoute(authResult.getUser().getUid());
                })
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(authTimeout);
                    setLoading(false);
                    showError(AuthErrors.describe(e, false));   // says what actually went wrong
                });
    }

    /**
     * Fetches the user's role from Firestore and navigates to the right screen.
     * If Firestore doesn't answer in a few seconds (blocked / offline network) we let a regular
     * user in anyway: SplashActivity routes admins correctly on the next launch.
     */
    private void fetchUserRoleAndRoute(String uid) {
        final Runnable firestoreTimeout = this::routeToMain;
        handler.postDelayed(firestoreTimeout, FIRESTORE_WAIT_MS);

        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    handler.removeCallbacks(firestoreTimeout);
                    if (routed) return;

                    if (!doc.exists()) {
                        // The account exists but its profile was never saved (for example registration
                        // was interrupted). Create it now instead of locking the user out.
                        repairMissingProfile(uid);
                        return;
                    }

                    Boolean isBanned = doc.getBoolean("isBanned");
                    if (Boolean.TRUE.equals(isBanned)) {
                        setLoading(false);
                        mAuth.signOut();
                        showError("Your account has been suspended.");
                        return;
                    }

                    String role = doc.getString("role");
                    routed = true;
                    setLoading(false);
                    if (Constants.ROLE_ADMIN.equals(role) || Constants.ROLE_STAFF.equals(role)) {
                        startActivity(new Intent(this, AdminActivity.class));
                    } else {
                        startActivity(new Intent(this, MainActivity.class));
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(firestoreTimeout);
                    // If Firestore fails, still let regular user in
                    routeToMain();
                });
    }

    /** Writes a default profile for an account that has none, then lets the user in. */
    private void repairMissingProfile(String uid) {
        FirebaseUser fbUser = mAuth.getCurrentUser();
        String email = fbUser != null && fbUser.getEmail() != null ? fbUser.getEmail() : "";
        String name  = fbUser != null && !TextUtils.isEmpty(fbUser.getDisplayName())
                ? fbUser.getDisplayName()
                : (email.contains("@") ? email.substring(0, email.indexOf('@')) : "Learner");

        handler.postDelayed(this::routeToMain, FIRESTORE_WAIT_MS);   // don't wait on the server forever
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .set(new User(uid, name, email))     // allowed by the rules: own document, role "user", zero progress
                .addOnCompleteListener(task -> routeToMain());
    }

    /** Goes to the regular app, once. */
    private void routeToMain() {
        if (routed || isFinishing() || isDestroyed()) return;
        routed = true;
        setLoading(false);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    /**
     * Sends a password reset email via Firebase.
     */
    private void sendPasswordReset() {
        String email = binding.etEmail.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError("Enter your email first");
            return;
        }
        mAuth.sendPasswordResetEmail(email)
                .addOnSuccessListener(unused ->
                        Toast.makeText(this,
                                "Reset email sent! Check your inbox.",
                                Toast.LENGTH_LONG).show())
                .addOnFailureListener(e ->
                        showError("Could not send reset email. Check the address and try again."));
    }

    // ── UI Helpers ───────────────────────────────────────────────

    /**
     * Toggles loading state: shows/hides spinner, disables/enables button.
     */
    private void setLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnLogin.setText(isLoading ? "Signing in..." : "Sign In");
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

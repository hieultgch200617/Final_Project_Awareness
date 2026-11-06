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
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityLoginBinding;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.ui.admin.AdminActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    // ViewBinding gives us direct access to every view in activity_login.xml
    // without needing findViewById() — safer and cleaner
    private ActivityLoginBinding binding;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

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

        // ── Firebase Sign In ─────────────────────────────────────
        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Login worked — now check the user's role
                    String uid = authResult.getUser().getUid();
                    fetchUserRoleAndRoute(uid);
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // Show a friendly error message
                    showError(getFriendlyErrorMessage(e.getMessage()));
                });
    }

    /**
     * Fetches the user's role from Firestore and navigates to the right screen.
     */
    private void fetchUserRoleAndRoute(String uid) {
        db.collection(Constants.COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    setLoading(false);
                    if (!doc.exists()) {
                        showError("Account data not found. Please contact support.");
                        return;
                    }

                    Boolean isBanned = doc.getBoolean("isBanned");
                    if (Boolean.TRUE.equals(isBanned)) {
                        mAuth.signOut();
                        showError("Your account has been suspended.");
                        return;
                    }

                    String role = doc.getString("role");
                    if (Constants.ROLE_ADMIN.equals(role) || Constants.ROLE_STAFF.equals(role)) {
                        startActivity(new Intent(this, AdminActivity.class));
                    } else {
                        startActivity(new Intent(this, MainActivity.class));
                    }
                    finish();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    // If Firestore fails, still let regular user in
                    startActivity(new Intent(this, MainActivity.class));
                    finish();
                });
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

    /**
     * Converts Firebase's raw error messages into user-friendly ones.
     */
    private String getFriendlyErrorMessage(String rawMessage) {
        if (rawMessage == null) return "Login failed. Please try again.";
        if (rawMessage.contains("no user record"))          return "No account found with this email.";
        if (rawMessage.contains("password is invalid"))     return "Incorrect password. Please try again.";
        if (rawMessage.contains("too many requests"))       return "Too many attempts. Please wait a moment.";
        if (rawMessage.contains("network"))                 return "Network error. Check your connection.";
        return "Login failed. Please try again.";
    }
}

package com.awareness.app.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.awareness.app.R;
import com.awareness.app.ui.MainActivity;
import com.awareness.app.ui.admin.AdminActivity;
import com.awareness.app.utils.Constants;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class SplashActivity extends AppCompatActivity {

    // How long to show the splash screen (in milliseconds)
    private static final int SPLASH_DELAY = 1500;

    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        mAuth = FirebaseAuth.getInstance();
        db    = FirebaseFirestore.getInstance();

        // Wait SPLASH_DELAY ms, then decide where to go
        new Handler(Looper.getMainLooper()).postDelayed(
                this::checkLoginState,
                SPLASH_DELAY
        );
    }

    /**
     * Checks Firebase Auth to see if a user session already exists.
     * If yes  → fetch their role from Firestore, then route accordingly.
     * If no   → go to LoginActivity.
     */
    private void checkLoginState() {
        FirebaseUser currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // No active session — show the login screen
            goToLogin();
            return;
        }

        // User is logged in — fetch their role from Firestore
        // so we know whether to show the regular app or the Admin panel
        db.collection(Constants.COLLECTION_USERS)
                .document(currentUser.getUid())
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        // Banned users must not get in on a saved session
                        if (Boolean.TRUE.equals(documentSnapshot.getBoolean("isBanned"))) {
                            mAuth.signOut();
                            goToLogin();
                            return;
                        }

                        String role = documentSnapshot.getString("role");

                        if (Constants.ROLE_ADMIN.equals(role) ||
                                Constants.ROLE_STAFF.equals(role)) {
                            goToAdmin();
                        } else {
                            goToMain();
                        }
                    } else {
                        // User doc missing — something went wrong, send to login
                        goToLogin();
                    }
                })
                .addOnFailureListener(e -> {
                    // Network error — still send to main; Firebase will sync when online
                    goToMain();
                });
    }

    // ── Navigation helpers ───────────────────────────────────────

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish(); // finish() removes this Activity from the back stack
        // so pressing Back on Login won't return to Splash
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        // Keep a notification's deep link (e.g. destination=quiz) alive through the splash
        String destination = getIntent().getStringExtra("destination");
        if (destination != null) intent.putExtra("destination", destination);
        startActivity(intent);
        finish();
    }

    private void goToAdmin() {
        startActivity(new Intent(this, AdminActivity.class));
        finish();
    }
}

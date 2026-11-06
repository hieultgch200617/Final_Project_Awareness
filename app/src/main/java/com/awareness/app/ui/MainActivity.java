package com.awareness.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityMainBinding;
import com.awareness.app.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private NavController navController;

    // Android 13+ needs a runtime OK before notifications (Daily Quiz reminders) can show
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupNavigation();
        askForNotificationPermission();
        handleDeepLink(savedInstanceState);
    }

    private void askForNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    /**
     * A Daily Quiz notification carries destination=quiz (set in AwarenessMessagingService /
     * the FCM data payload and forwarded by SplashActivity). Open the quiz on top of Academy.
     * Skipped on rotation/recreation so it doesn't re-open every time.
     */
    private void handleDeepLink(Bundle savedInstanceState) {
        if (savedInstanceState != null) return;
        if ("quiz".equals(getIntent().getStringExtra("destination"))) {
            navController.navigate(R.id.action_academy_to_quiz);
        }
    }

    /**
     * Connects the BottomNavigationView to the NavController.
     * NavigationUI.setupWithNavController() handles ALL tab switching
     * automatically — no manual click listeners needed.
     */
    private void setupNavigation() {
        // Get the NavHostFragment from the layout
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.navHostFragment);

        // Get the NavController from it
        navController = navHostFragment.getNavController();

        // Wire the bottom nav bar to the NavController
        // This one line handles: tab switching, back stack, selected state
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
    }

    /**
     * Signs the user out and returns to LoginActivity.
     * Called from ProfileFragment's logout button.
     */
    public void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        // Clear back stack — user can't press Back to return after logout
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /**
     * Navigates to a destination in the nav graph.
     * Fragments can call this to trigger navigation from MainActivity.
     */
    public NavController getNavController() {
        return navController;
    }
}

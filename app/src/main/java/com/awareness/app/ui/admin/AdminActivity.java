package com.awareness.app.ui.admin;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.awareness.app.R;
import com.awareness.app.databinding.ActivityAdminBinding;
import com.awareness.app.ui.auth.LoginActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;

public class AdminActivity extends AppCompatActivity {

    private ActivityAdminBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAdminBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.adminNavHostFragment);
        NavController navController = navHost.getNavController();
        NavigationUI.setupWithNavController(binding.adminBottomNavigation, navController);

        // The 5th tab is not a screen: it asks to log out instead of navigating.
        // Returning false keeps the previously selected tab highlighted.
        binding.adminBottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.logoutAction) {
                confirmLogout();
                return false;
            }
            return NavigationUI.onNavDestinationSelected(item, navController);
        });
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Log out?")
                .setMessage("You'll need to sign in again to use the admin tools.")
                .setPositiveButton("Log out", (d, w) -> logout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    public void logout() {
        FirebaseAuth.getInstance().signOut();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}

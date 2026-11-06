package com.awareness.app.ui.admin;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.awareness.app.adapter.UserAdapter;
import com.awareness.app.databinding.FragmentUserManagementBinding;
import com.awareness.app.model.User;
import com.awareness.app.utils.Constants;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin → Users tab.
 *
 *  - Lists the top 100 users by XP, or searches by email prefix (case-sensitive,
 *    because Firestore prefix queries are).
 *  - Tap a user to ban/unban them or (admins only) change their role.
 *
 * Enforcement lives in firestore.rules, NOT in this screen:
 *   admin  → may change anything
 *   staff  → may only ban/unban plain users
 * so even a tampered app can't do more than the rules allow.
 */
public class UserManagementFragment extends Fragment implements UserAdapter.Listener {

    private FragmentUserManagementBinding binding;
    private FirebaseFirestore db;
    private UserAdapter adapter;

    private String myUid = "";
    private boolean iAmAdmin = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentUserManagementBinding.inflate(inflater, container, false);
        db = FirebaseFirestore.getInstance();

        FirebaseUser me = FirebaseAuth.getInstance().getCurrentUser();
        myUid = me != null ? me.getUid() : "";
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new UserAdapter(myUid, this);
        binding.rvUsers.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvUsers.setAdapter(adapter);

        binding.etSearch.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                loadUsers(textOf());
                return true;
            }
            return false;
        });
        // Clearing the search box (the ✕ icon) restores the default list
        binding.tilSearch.setEndIconOnClickListener(v -> {
            binding.etSearch.setText("");
            hideKeyboard();
            loadUsers("");
        });

        loadMyRole();
        loadUsers("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Data ──────────────────────────────────────────────────────

    private void loadMyRole() {
        if (myUid.isEmpty()) return;
        db.collection(Constants.COLLECTION_USERS).document(myUid).get()
                .addOnSuccessListener(doc ->
                        iAmAdmin = Constants.ROLE_ADMIN.equals(doc.getString("role")));
    }

    private void loadUsers(String emailPrefix) {
        if (binding == null) return;
        showLoading(true);

        Query q;
        if (emailPrefix.isEmpty()) {
            q = db.collection(Constants.COLLECTION_USERS)
                    .orderBy("xpPoints", Query.Direction.DESCENDING)
                    .limit(100);
        } else {
            q = db.collection(Constants.COLLECTION_USERS)
                    .orderBy("email")
                    .startAt(emailPrefix)
                    .endAt(emailPrefix + "\uf8ff")
                    .limit(100);
        }

        q.get().addOnSuccessListener(snap -> {
            if (binding == null) return;
            List<User> users = new ArrayList<>(snap.toObjects(User.class));
            adapter.submit(users);
            showLoading(false);
            binding.tvEmpty.setVisibility(users.isEmpty() ? View.VISIBLE : View.GONE);
            binding.tvInfo.setText(emailPrefix.isEmpty()
                    ? "Top " + users.size() + " users by XP"
                    : users.size() + " result(s) for \"" + emailPrefix + "\"");
        }).addOnFailureListener(e -> {
            if (binding == null) return;
            showLoading(false);
            binding.tvEmpty.setText("Couldn't load users.\n" + e.getMessage());
            binding.tvEmpty.setVisibility(View.VISIBLE);
        });
    }

    // ── Actions ───────────────────────────────────────────────────

    @Override
    public void onUserClicked(User user) {
        if (user.getUserId() == null) return;

        if (user.getUserId().equals(myUid)) {
            toast("You can't change your own account here.");
            return;
        }

        String role = user.getRole() != null ? user.getRole() : Constants.ROLE_USER;
        boolean targetIsPlainUser = Constants.ROLE_USER.equals(role);

        // Build the menu from what the rules will actually allow for this admin/staff member
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        if (iAmAdmin || targetIsPlainUser) {
            if (user.isBanned()) {
                labels.add("✅ Unban user");
                actions.add(() -> setBanned(user, false));
            } else {
                labels.add("🚫 Ban user");
                actions.add(() -> confirmBan(user));
            }
        }

        if (iAmAdmin) {
            if (!Constants.ROLE_USER.equals(role)) {
                labels.add("Make regular user");
                actions.add(() -> setRole(user, Constants.ROLE_USER));
            }
            if (!Constants.ROLE_STAFF.equals(role)) {
                labels.add("Make staff (can ban users)");
                actions.add(() -> setRole(user, Constants.ROLE_STAFF));
            }
            if (!Constants.ROLE_ADMIN.equals(role)) {
                labels.add("Make admin (full access)");
                actions.add(() -> confirmMakeAdmin(user));
            }
        }

        if (labels.isEmpty()) {
            toast("Only an admin can manage staff and admin accounts.");
            return;
        }

        String name = user.getDisplayName() != null ? user.getDisplayName() : user.getEmail();
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(name)
                .setItems(labels.toArray(new String[0]), (d, which) -> actions.get(which).run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmBan(User user) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ban this user?")
                .setMessage("They'll be signed out on their next app launch and can no longer "
                        + "write any data (reports, XP, scans) right away.")
                .setPositiveButton("Ban", (d, w) -> setBanned(user, true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmMakeAdmin(User user) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Give full admin access?")
                .setMessage("Admins can change anyone's role, edit content and see all user data. "
                        + "Only do this for people you fully trust.")
                .setPositiveButton("Make admin", (d, w) -> setRole(user, Constants.ROLE_ADMIN))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setBanned(User user, boolean banned) {
        // Field name "isBanned" matches @PropertyName on User.isBanned() and firestore.rules
        db.collection(Constants.COLLECTION_USERS).document(user.getUserId())
                .update("isBanned", banned)
                .addOnSuccessListener(v -> {
                    user.setBanned(banned);
                    if (binding != null) adapter.refresh(user);
                    toast(banned ? "User banned." : "User unbanned.");
                })
                .addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
    }

    private void setRole(User user, String newRole) {
        db.collection(Constants.COLLECTION_USERS).document(user.getUserId())
                .update("role", newRole)
                .addOnSuccessListener(v -> {
                    user.setRole(newRole);
                    if (binding != null) adapter.refresh(user);
                    toast("Role changed to " + newRole + ".");
                })
                .addOnFailureListener(e -> toast("Failed: " + e.getMessage()));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private void showLoading(boolean loading) {
        binding.progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) binding.tvEmpty.setVisibility(View.GONE);
    }

    private String textOf() {
        return binding.etSearch.getText() == null
                ? "" : binding.etSearch.getText().toString().trim();
    }

    private void hideKeyboard() {
        if (!isAdded()) return;
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && binding != null) {
            imm.hideSoftInputFromWindow(binding.etSearch.getWindowToken(), 0);
        }
    }

    private void toast(String msg) {
        if (isAdded()) Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}

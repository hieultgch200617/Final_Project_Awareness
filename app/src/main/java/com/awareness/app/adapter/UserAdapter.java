package com.awareness.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.databinding.ItemUserRowBinding;
import com.awareness.app.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** List of users for Admin → User Management. */
public class UserAdapter extends RecyclerView.Adapter<UserAdapter.UserViewHolder> {

    public interface Listener {
        void onUserClicked(User user);
    }

    private final List<User> items = new ArrayList<>();
    private final Listener listener;
    private final String myUid;

    public UserAdapter(String myUid, Listener listener) {
        this.myUid = myUid;
        this.listener = listener;
    }

    public void submit(List<User> users) {
        items.clear();
        items.addAll(users);
        notifyDataSetChanged();
    }

    /** Refresh one row after its data changed locally (ban / role change). */
    public void refresh(User user) {
        int pos = items.indexOf(user);
        if (pos >= 0) notifyItemChanged(pos);
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemUserRowBinding b = ItemUserRowBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new UserViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class UserViewHolder extends RecyclerView.ViewHolder {
        private final ItemUserRowBinding b;

        UserViewHolder(ItemUserRowBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(User u) {
            String name = u.getDisplayName() != null && !u.getDisplayName().isEmpty()
                    ? u.getDisplayName() : "(no name)";
            boolean isMe = u.getUserId() != null && u.getUserId().equals(myUid);

            b.tvAvatar.setText(name.substring(0, 1).toUpperCase(Locale.getDefault()));
            b.tvName.setText(isMe ? name + "  (you)" : name);
            b.tvEmail.setText(u.getEmail() != null ? u.getEmail() : "");
            b.tvRoleBadge.setText((u.getRole() != null ? u.getRole() : "user").toUpperCase(Locale.US));
            b.tvBanned.setVisibility(u.isBanned() ? View.VISIBLE : View.GONE);
            b.tvStats.setText("Lv " + u.getLevel() + "\n" + u.getXpPoints() + " XP");

            b.getRoot().setOnClickListener(v -> listener.onUserClicked(u));
        }
    }
}

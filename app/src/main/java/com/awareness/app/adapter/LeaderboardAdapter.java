package com.awareness.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.R;
import com.awareness.app.model.LeaderboardEntry;

import java.util.ArrayList;
import java.util.List;

public class LeaderboardAdapter
        extends RecyclerView.Adapter<LeaderboardAdapter.LeaderboardViewHolder> {

    private List<LeaderboardEntry> entries = new ArrayList<>();
    private final Context context;
    private final String currentUserId;

    public LeaderboardAdapter(Context context, String currentUserId) {
        this.context       = context;
        this.currentUserId = currentUserId;
    }

    public void setEntries(List<LeaderboardEntry> newEntries) {
        this.entries = newEntries;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LeaderboardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_leaderboard_row, parent, false);
        return new LeaderboardViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull LeaderboardViewHolder holder, int position) {
        holder.bind(entries.get(position));
    }

    @Override
    public int getItemCount() { return entries.size(); }

    class LeaderboardViewHolder extends RecyclerView.ViewHolder {

        private final CardView cardRow;
        private final TextView tvRank, tvAvatar, tvName,
                tvRegion, tvStreak, tvXp, tvLevel;

        LeaderboardViewHolder(@NonNull View itemView) {
            super(itemView);
            cardRow   = itemView.findViewById(R.id.cardRow);
            tvRank    = itemView.findViewById(R.id.tvRank);
            tvAvatar  = itemView.findViewById(R.id.tvAvatar);
            tvName    = itemView.findViewById(R.id.tvName);
            tvRegion  = itemView.findViewById(R.id.tvRegion);
            tvStreak  = itemView.findViewById(R.id.tvStreak);
            tvXp      = itemView.findViewById(R.id.tvXp);
            tvLevel   = itemView.findViewById(R.id.tvLevel);
        }

        void bind(LeaderboardEntry entry) {

            // ── Rank ─────────────────────────────────────────────
            tvRank.setText("#" + entry.getRank());

            // ── Avatar initial ───────────────────────────────────
            tvAvatar.setText(entry.getAvatarLetter());

            // ── Name ─────────────────────────────────────────────
            tvName.setText(entry.getDisplayName() != null
                    ? entry.getDisplayName() : "Anonymous");

            // ── Region ───────────────────────────────────────────
            tvRegion.setText(entry.getRegion() != null && !entry.getRegion().isEmpty()
                    ? "📍 " + entry.getRegion() : "");

            // ── Streak (only show if > 0) ─────────────────────────
            if (entry.getStreakCount() > 0) {
                tvStreak.setText(" · 🔥 " + entry.getStreakCount());
                tvStreak.setVisibility(View.VISIBLE);
            } else {
                tvStreak.setVisibility(View.GONE);
            }

            // ── XP + Level ───────────────────────────────────────
            tvXp.setText(entry.getXpPoints() + " XP");
            tvLevel.setText("Lv. " + entry.getLevel());

            // ── Highlight current user's row ──────────────────────
            // If this entry belongs to the logged-in user, give the
            // card a light blue tint so they can find themselves instantly
            boolean isCurrentUser = entry.getUserId() != null
                    && entry.getUserId().equals(currentUserId);

            if (isCurrentUser) {
                cardRow.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.highlight_row));
                tvName.setTextColor(
                        ContextCompat.getColor(context, R.color.brand_navy));
                tvRank.setTextColor(
                        ContextCompat.getColor(context, R.color.brand_blue));
            } else {
                cardRow.setCardBackgroundColor(
                        ContextCompat.getColor(context, R.color.surface));
                tvName.setTextColor(
                        ContextCompat.getColor(context, R.color.text_primary));
                tvRank.setTextColor(
                        ContextCompat.getColor(context, R.color.text_secondary));
            }
        }
    }
}

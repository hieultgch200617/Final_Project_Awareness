package com.awareness.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.R;
import com.awareness.app.model.Lesson;
import com.awareness.app.utils.Constants;
import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;

public class LessonAdapter extends RecyclerView.Adapter<LessonAdapter.LessonViewHolder> {

    // The data list this adapter displays
    private List<Lesson> lessons = new ArrayList<>();

    // Callback interface — tells the fragment which lesson was tapped
    // This pattern (interface + listener) is how Android passes click events
    // from an adapter back up to the fragment/activity
    public interface OnLessonClickListener {
        void onLessonClick(Lesson lesson);
    }

    private final OnLessonClickListener listener;
    private final Context context;

    public LessonAdapter(Context context, OnLessonClickListener listener) {
        this.context  = context;
        this.listener = listener;
    }

    // ── Step 2: Create a blank card view ─────────────────────────
    @NonNull
    @Override
    public LessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate item_lesson_card.xml to create the card view
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_lesson_card, parent, false);
        return new LessonViewHolder(view);
    }

    // ── Step 3: Fill a card with a specific lesson's data ─────────
    @Override
    public void onBindViewHolder(@NonNull LessonViewHolder holder, int position) {
        Lesson lesson = lessons.get(position);
        holder.bind(lesson);
    }

    // ── Step 1: How many items? ───────────────────────────────────
    @Override
    public int getItemCount() {
        return lessons.size();
    }

    /**
     * Updates the list and refreshes the RecyclerView.
     * Called whenever Firestore returns new data or the filter changes.
     */
    public void setLessons(List<Lesson> newLessons) {
        this.lessons = newLessons;
        notifyDataSetChanged(); // Tells RecyclerView to redraw all cards
    }

    // ── ViewHolder: holds references to the views inside one card ─
    class LessonViewHolder extends RecyclerView.ViewHolder {

        private final ImageView imgThumbnail;
        private final ImageView imgType;
        private final TextView  tvCategory;
        private final TextView  tvTitle;
        private final TextView  tvXpReward;

        LessonViewHolder(@NonNull View itemView) {
            super(itemView);
            imgThumbnail = itemView.findViewById(R.id.imgThumbnail);
            imgType      = itemView.findViewById(R.id.imgType);
            tvCategory   = itemView.findViewById(R.id.tvCategory);
            tvTitle      = itemView.findViewById(R.id.tvTitle);
            tvXpReward   = itemView.findViewById(R.id.tvXpReward);
        }

        void bind(Lesson lesson) {
            // Fill in the text fields
            tvTitle.setText(lesson.getTitle());
            tvCategory.setText(lesson.getCategory());
            tvXpReward.setText("⭐ +" + lesson.getXpReward() + " XP");

            // Set the type icon based on lesson type
            switch (lesson.getType()) {
                case Constants.LESSON_TYPE_VIDEO:
                    imgType.setImageResource(R.drawable.ic_play_circle);
                    break;
                case Constants.LESSON_TYPE_IMAGE:
                    imgType.setImageResource(R.drawable.ic_image);
                    break;
                default: // text
                    imgType.setImageResource(R.drawable.ic_article);
                    break;
            }

            // Load thumbnail from URL using Glide
            // Glide handles: downloading, caching, resizing — all automatically
            if (lesson.getThumbnailUrl() != null && !lesson.getThumbnailUrl().isEmpty()) {
                Glide.with(context)
                        .load(lesson.getThumbnailUrl())
                        .placeholder(R.drawable.ic_image)   // shown while loading
                        .error(R.drawable.ic_image)          // shown if load fails
                        .centerCrop()
                        .into(imgThumbnail);
            } else {
                imgThumbnail.setImageResource(R.drawable.ic_image);
            }

            // Handle card tap → notify the fragment via our listener
            itemView.setOnClickListener(v -> listener.onLessonClick(lesson));
        }
    }
}

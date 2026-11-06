package com.awareness.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.databinding.ItemCmsLessonBinding;
import com.awareness.app.model.Lesson;

import java.util.ArrayList;
import java.util.List;

/** Lesson list for Admin → Content (shows drafts too, with a publish switch). */
public class CmsLessonAdapter extends RecyclerView.Adapter<CmsLessonAdapter.LessonViewHolder> {

    public interface Listener {
        void onEdit(Lesson lesson);
        void onDelete(Lesson lesson);
        void onTogglePublished(Lesson lesson, boolean published);
    }

    private final List<Lesson> items = new ArrayList<>();
    private final Listener listener;

    public CmsLessonAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<Lesson> lessons) {
        items.clear();
        items.addAll(lessons);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LessonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCmsLessonBinding b = ItemCmsLessonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new LessonViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull LessonViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class LessonViewHolder extends RecyclerView.ViewHolder {
        private final ItemCmsLessonBinding b;

        LessonViewHolder(ItemCmsLessonBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(Lesson l) {
            b.tvTitle.setText(l.getTitle() != null ? l.getTitle() : "(untitled)");
            b.tvMeta.setText(
                    (l.getCategory() != null ? l.getCategory() : "—")
                            + "  ·  " + (l.getType() != null ? l.getType() : "—")
                            + "  ·  " + l.getXpReward() + " XP"
                            + "  ·  #" + l.getOrderIndex());
            b.tvDraft.setVisibility(l.isPublished() ? View.GONE : View.VISIBLE);

            // Detach the listener before setChecked(), or recycling a row would fire a bogus update
            b.switchPublished.setOnCheckedChangeListener(null);
            b.switchPublished.setChecked(l.isPublished());
            b.switchPublished.setOnCheckedChangeListener(
                    (button, checked) -> listener.onTogglePublished(l, checked));

            b.btnEdit.setOnClickListener(v -> listener.onEdit(l));
            b.btnDelete.setOnClickListener(v -> listener.onDelete(l));
        }
    }
}

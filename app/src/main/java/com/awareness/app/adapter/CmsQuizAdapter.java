package com.awareness.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.databinding.ItemCmsLessonBinding;
import com.awareness.app.model.QuizQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Daily-quiz questions for Admin → Content → Daily Quiz.
 * Reuses the lesson row layout (item_cms_lesson): title = the question, plus a publish switch,
 * Edit and Delete.
 */
public class CmsQuizAdapter extends RecyclerView.Adapter<CmsQuizAdapter.QuizViewHolder> {

    public interface Listener {
        void onEdit(QuizQuestion question);
        void onDelete(QuizQuestion question);
        void onTogglePublished(QuizQuestion question, boolean published);
    }

    private final List<QuizQuestion> items = new ArrayList<>();
    private final Listener listener;

    public CmsQuizAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<QuizQuestion> questions) {
        items.clear();
        items.addAll(questions);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public QuizViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCmsLessonBinding b = ItemCmsLessonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new QuizViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull QuizViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class QuizViewHolder extends RecyclerView.ViewHolder {
        private final ItemCmsLessonBinding b;

        QuizViewHolder(ItemCmsLessonBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(QuizQuestion q) {
            b.tvTitle.setText(q.getQuestion() != null ? q.getQuestion() : "(no question text)");

            int optionCount = q.getOptions() != null ? q.getOptions().size() : 0;
            String answer = q.isValid() ? String.valueOf((char) ('A' + q.getCorrectIndex())) : "?";
            b.tvMeta.setText(
                    (q.getCategory() != null && !q.getCategory().isEmpty() ? q.getCategory() : "No category")
                            + "  ·  " + optionCount + " options"
                            + "  ·  answer: " + answer
                            + (q.isValid() ? "" : "  ·  ⚠ needs fixing"));

            b.tvDraft.setText("DRAFT — not used in the daily quiz");
            b.tvDraft.setVisibility(q.isPublished() ? View.GONE : View.VISIBLE);

            // Detach the listener before setChecked(), or recycling a row would fire a bogus update
            b.switchPublished.setOnCheckedChangeListener(null);
            b.switchPublished.setChecked(q.isPublished());
            b.switchPublished.setOnCheckedChangeListener(
                    (button, checked) -> listener.onTogglePublished(q, checked));

            b.btnEdit.setOnClickListener(v -> listener.onEdit(q));
            b.btnDelete.setOnClickListener(v -> listener.onDelete(q));
        }
    }
}

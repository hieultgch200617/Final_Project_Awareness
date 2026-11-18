package com.awareness.app.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.databinding.ItemCmsLessonBinding;
import com.awareness.app.model.ScamScenario;

import java.util.ArrayList;
import java.util.List;

/**
 * Scam Lab scenarios for Admin → Content → Scam Lab.
 * Reuses the lesson row layout (item_cms_lesson): title = the scenario title, plus a publish
 * switch, Edit and Delete.
 */
public class CmsScenarioAdapter extends RecyclerView.Adapter<CmsScenarioAdapter.ScenarioViewHolder> {

    public interface Listener {
        void onEdit(ScamScenario scenario);
        void onDelete(ScamScenario scenario);
        void onTogglePublished(ScamScenario scenario, boolean published);
    }

    private final List<ScamScenario> items = new ArrayList<>();
    private final Listener listener;

    public CmsScenarioAdapter(Listener listener) {
        this.listener = listener;
    }

    public void submit(List<ScamScenario> scenarios) {
        items.clear();
        items.addAll(scenarios);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScenarioViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCmsLessonBinding b = ItemCmsLessonBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ScenarioViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull ScenarioViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ScenarioViewHolder extends RecyclerView.ViewHolder {
        private final ItemCmsLessonBinding b;

        ScenarioViewHolder(ItemCmsLessonBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(ScamScenario s) {
            b.tvTitle.setText(s.getTitle() != null ? s.getTitle() : "(untitled scenario)");

            int flags = s.getRedFlags() != null ? s.getRedFlags().size() : 0;
            b.tvMeta.setText(
                    (s.getCategory() != null ? s.getCategory() : "—")
                            + "  ·  " + (s.getScenarioType() != null ? s.getScenarioType() : "sms")
                            + "  ·  " + flags + (flags == 1 ? " red flag" : " red flags")
                            + "  ·  base " + s.getXpReward() + " XP");

            b.tvDraft.setText("DRAFT — not used in Scam Lab");
            b.tvDraft.setVisibility(s.isPublished() ? View.GONE : View.VISIBLE);

            // Detach the listener before setChecked(), or recycling a row would fire a bogus update
            b.switchPublished.setOnCheckedChangeListener(null);
            b.switchPublished.setChecked(s.isPublished());
            b.switchPublished.setOnCheckedChangeListener(
                    (button, checked) -> listener.onTogglePublished(s, checked));

            b.btnEdit.setOnClickListener(v -> listener.onEdit(s));
            b.btnDelete.setOnClickListener(v -> listener.onDelete(s));
        }
    }
}

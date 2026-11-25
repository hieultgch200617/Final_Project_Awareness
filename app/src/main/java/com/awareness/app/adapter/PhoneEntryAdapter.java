package com.awareness.app.adapter;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.R;
import com.awareness.app.databinding.ItemPhoneEntryBinding;
import com.awareness.app.model.PhoneEntry;

import java.util.ArrayList;
import java.util.List;

/** One row per checked number in the Blacklist / Graylist / Whitelist sheet. */
public class PhoneEntryAdapter extends RecyclerView.Adapter<PhoneEntryAdapter.EntryViewHolder> {

    public interface Listener {
        void onOpen(PhoneEntry entry);
        void onRemove(PhoneEntry entry);
    }

    private final List<PhoneEntry> items = new ArrayList<>();
    private final Listener listener;
    private final boolean serverMode;   // admin view of the server list: "Added …" instead of "Checked N times"

    public PhoneEntryAdapter(Listener listener, boolean serverMode) {
        this.listener = listener;
        this.serverMode = serverMode;
    }

    public void submit(List<PhoneEntry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public EntryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPhoneEntryBinding b = ItemPhoneEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new EntryViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull EntryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class EntryViewHolder extends RecyclerView.ViewHolder {
        private final ItemPhoneEntryBinding b;

        EntryViewHolder(ItemPhoneEntryBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(PhoneEntry e) {
            int colorRes = PhoneEntry.LIST_BLACK.equals(e.getList()) ? R.color.rating_malicious
                    : PhoneEntry.LIST_GRAY.equals(e.getList())       ? R.color.rating_suspicious
                    :                                                  R.color.rating_safe;
            b.tvDot.setTextColor(ContextCompat.getColor(b.getRoot().getContext(), colorRes));

            b.tvNumber.setText(e.getNumber() != null ? e.getNumber() : e.getId());

            String carrier  = e.getCarrier()  != null && !e.getCarrier().isEmpty()  ? e.getCarrier()  : "Unknown carrier";
            String lineType = e.getLineType() != null && !e.getLineType().isEmpty() ? e.getLineType() : "Unknown type";
            b.tvMeta.setText(carrier + "  ·  " + lineType + "  ·  "
                    + (e.getFraudScore() != null ? "score " + e.getFraudScore() + "/100" : "no score"));

            long times = e.getCheckCount() != null ? e.getCheckCount() : 1;
            String when = e.getCheckedAt() != null
                    ? DateUtils.getRelativeTimeSpanString(e.getCheckedAt().getTime(),
                            System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
                    : "just now";
            b.tvWhen.setText(serverMode
                    ? "Added to the server list  ·  " + when
                    : "Checked " + times + (times == 1 ? " time" : " times") + "  ·  last " + when);

            b.getRoot().setOnClickListener(v -> listener.onOpen(e));
            b.btnRemove.setOnClickListener(v -> listener.onRemove(e));
        }
    }
}

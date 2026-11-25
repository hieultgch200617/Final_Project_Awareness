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
import com.awareness.app.model.SiteEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * One row per checked website in the Blacklist / Graylist / Whitelist sheet.
 * Reuses the phone-entry row layout (item_phone_entry): same dot, 3 lines of text and a Remove button.
 */
public class SiteEntryAdapter extends RecyclerView.Adapter<SiteEntryAdapter.SiteViewHolder> {

    public interface Listener {
        void onOpen(SiteEntry entry);
        void onRemove(SiteEntry entry);
    }

    private final List<SiteEntry> items = new ArrayList<>();
    private final Listener listener;
    private final boolean serverMode;     // admin view of the server list: "Added …" instead of "Checked N times"

    public SiteEntryAdapter(Listener listener, boolean serverMode) {
        this.listener = listener;
        this.serverMode = serverMode;
    }

    public void submit(List<SiteEntry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    /** Plain-words version of Google Safe Browsing's threat type. */
    public static String describeThreat(String threatType) {
        if (threatType == null || threatType.isEmpty()) return "No threats found by Google Safe Browsing";
        switch (threatType) {
            case "MALWARE":                         return "Malware";
            case "PHISHING":                        return "Phishing (tries to steal logins or money)";
            case "UNWANTED_SOFTWARE":               return "Unwanted software";
            case "POTENTIALLY_HARMFUL_APPLICATION": return "Potentially harmful app";
            default:                                return threatType;
        }
    }

    @NonNull
    @Override
    public SiteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPhoneEntryBinding b = ItemPhoneEntryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new SiteViewHolder(b);
    }

    @Override
    public void onBindViewHolder(@NonNull SiteViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class SiteViewHolder extends RecyclerView.ViewHolder {
        private final ItemPhoneEntryBinding b;

        SiteViewHolder(ItemPhoneEntryBinding binding) {
            super(binding.getRoot());
            this.b = binding;
        }

        void bind(SiteEntry e) {
            int colorRes = PhoneEntry.LIST_BLACK.equals(e.getList()) ? R.color.rating_malicious
                    : PhoneEntry.LIST_GRAY.equals(e.getList())       ? R.color.rating_suspicious
                    :                                                  R.color.rating_safe;
            b.tvDot.setTextColor(ContextCompat.getColor(b.getRoot().getContext(), colorRes));

            b.tvNumber.setText(e.getUrl() != null ? e.getUrl() : e.getId());
            b.tvMeta.setText(describeThreat(e.getThreatType()));

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

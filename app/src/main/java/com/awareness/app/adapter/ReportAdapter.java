package com.awareness.app.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.awareness.app.R;
import com.awareness.app.model.ScamReport;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<ScamReport> reports = new ArrayList<>();
    private final Context context;
    private final FirebaseFirestore db;
    private final String currentUserId;

    public ReportAdapter(Context context, FirebaseFirestore db, String userId) {
        this.context       = context;
        this.db            = db;
        this.currentUserId = userId;
    }

    public void setReports(List<ScamReport> newReports) {
        this.reports = newReports;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_report_card, parent, false);
        return new ReportViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        holder.bind(reports.get(position));
    }

    @Override
    public int getItemCount() { return reports.size(); }

    class ReportViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvScamType, tvVerified, tvRegion,
                tvDescription, tvTime, tvUpvotes;
        private final LinearLayout btnUpvote;

        ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvScamType   = itemView.findViewById(R.id.tvScamType);
            tvVerified   = itemView.findViewById(R.id.tvVerified);
            tvRegion     = itemView.findViewById(R.id.tvRegion);
            tvDescription = itemView.findViewById(R.id.tvDescription);
            tvTime       = itemView.findViewById(R.id.tvTime);
            tvUpvotes    = itemView.findViewById(R.id.tvUpvotes);
            btnUpvote    = itemView.findViewById(R.id.btnUpvote);
        }

        void bind(ScamReport report) {
            tvScamType.setText(report.getScamType());
            tvRegion.setText("📍 " + report.getRegion());
            tvDescription.setText(report.getDescription());
            tvUpvotes.setText(String.valueOf(report.getUpvotes()));

            // Verified badge
            tvVerified.setVisibility(report.isVerified() ? View.VISIBLE : View.GONE);

            // Time ago
            if (report.getReportedAt() != null) {
                tvTime.setText(getTimeAgo(report.getReportedAt().getTime()));
            } else {
                tvTime.setText("Just now");
            }

            // Upvote button — increments count in Firestore
            btnUpvote.setOnClickListener(v -> {
                if (report.getReportId() == null) return;
                db.collection("scam_reports")
                        .document(report.getReportId())
                        .update("upvotes", FieldValue.increment(1));
            });
        }

        /**
         * Returns a human-friendly "X time ago" string.
         */
        private String getTimeAgo(long timestamp) {
            long diff = System.currentTimeMillis() - timestamp;
            long minutes = diff / 60000;
            if (minutes < 1)   return "Just now";
            if (minutes < 60)  return minutes + " min ago";
            long hours = minutes / 60;
            if (hours < 24)    return hours + " hour" + (hours > 1 ? "s" : "") + " ago";
            long days = hours / 24;
            return days + " day" + (days > 1 ? "s" : "") + " ago";
        }
    }
}

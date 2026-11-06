package com.awareness.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SafeBrowsingResponse {

    // "matches" is null/empty when URL is safe
    @SerializedName("matches")
    private List<ThreatMatch> matches;

    public List<ThreatMatch> getMatches() { return matches; }

    public boolean isSafe() {
        return matches == null || matches.isEmpty();
    }

    /**
     * Returns the most severe threat type found.
     * Priority: MALWARE > SOCIAL_ENGINEERING > UNWANTED_SOFTWARE > other
     */
    public String getPrimaryThreatType() {
        if (isSafe()) return null;
        for (ThreatMatch m : matches) {
            if ("MALWARE".equals(m.getThreatType())) return "MALWARE";
        }
        for (ThreatMatch m : matches) {
            if ("SOCIAL_ENGINEERING".equals(m.getThreatType())) return "PHISHING";
        }
        return matches.get(0).getThreatType();
    }

    // ── Inner class ───────────────────────────────────────────────

    public static class ThreatMatch {
        @SerializedName("threatType")
        private String threatType;

        @SerializedName("platformType")
        private String platformType;

        public String getThreatType()   { return threatType; }
        public String getPlatformType() { return platformType; }
    }
}

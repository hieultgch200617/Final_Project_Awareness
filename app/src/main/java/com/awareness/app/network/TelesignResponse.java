package com.awareness.app.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class TelesignResponse {

    @SerializedName("phone_type")
    private PhoneType phoneType;

    @SerializedName("carrier")
    private Carrier carrier;

    @SerializedName("numbering")
    private Numbering numbering;

    @SerializedName("risk")
    private Risk risk;

    @SerializedName("status")
    private Status status;

    // ── Convenience helpers used by the UI ───────────────────────

    public boolean isVoip() {
        return phoneType != null && "VOIP".equalsIgnoreCase(phoneType.description);
    }

    public String getCarrierName() {
        return carrier != null && carrier.name != null ? carrier.name : "Unknown";
    }

    public int getRiskScore() {
        return risk != null ? risk.score : 0;
    }

    /**
     * Maps Telesign's recommendation to our app's 3-tier rating system.
     * allow  → SAFE
     * flag   → SUSPICIOUS
     * block  → MALICIOUS
     */
    public String getAppRating() {
        if (risk == null || risk.recommendation == null) return "UNKNOWN";
        switch (risk.recommendation.toLowerCase()) {
            case "allow": return "SAFE";
            case "flag":  return "SUSPICIOUS";
            case "block": return "MALICIOUS";
            default:      return "SUSPICIOUS";
        }
    }

    public String getPhoneTypeDescription() {
        return phoneType != null && phoneType.description != null
                ? phoneType.description : "Unknown";
    }

    public String getCountryCode() {
        if (numbering == null || numbering.original == null) return "";
        return numbering.original.countryCode != null
                ? "+" + numbering.original.countryCode : "";
    }

    // ── Getters ───────────────────────────────────────────────────
    public PhoneType  getPhoneType()  { return phoneType; }
    public Carrier    getCarrier()    { return carrier; }
    public Risk       getRisk()       { return risk; }
    public Status     getStatus()     { return status; }

    // ── Inner classes ─────────────────────────────────────────────

    public static class PhoneType {
        @SerializedName("code")
        public String code;

        @SerializedName("description")
        public String description;   // "MOBILE", "VOIP", "FIXED_LINE", "PREPAID"
    }

    public static class Carrier {
        @SerializedName("name")
        public String name;          // e.g. "Viettel", "Twilio"
    }

    public static class Numbering {
        @SerializedName("original")
        public Original original;

        public static class Original {
            @SerializedName("country_code")
            public String countryCode;    // "84" for Vietnam
        }
    }

    public static class Risk {
        @SerializedName("score")
        public int score;               // 0–1000

        @SerializedName("recommendation")
        public String recommendation;   // "allow", "flag", "block"

        @SerializedName("insights")
        public List<Insight> insights;  // e.g. [{"label": "fraud_risk"}]

        public static class Insight {
            @SerializedName("label")
            public String label;
        }
    }

    public static class Status {
        @SerializedName("code")
        public int code;               // 300 = success

        @SerializedName("description")
        public String description;
    }
}

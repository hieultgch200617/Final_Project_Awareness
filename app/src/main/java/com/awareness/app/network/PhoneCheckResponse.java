package com.awareness.app.network;

import com.awareness.app.utils.Constants;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * IPQualityScore phone-validation response (only the fields the app uses).
 * Every field that IPQS can omit or send as null is a boxed type, so a missing value never
 * becomes a misleading "false" or "0".
 *
 * Sample (abridged):
 * {"success":true,"message":"Phone is valid.","valid":true,"fraud_score":0,"recent_abuse":false,
 *  "VOIP":false,"prepaid":false,"risky":false,"active":true,"carrier":"Movistar",
 *  "line_type":"Wireless","country":"AR","dialing_code":54,"active_status":"Active Line"}
 */
public class PhoneCheckResponse {

    /** Used when we can't say anything useful about the number. */
    public static final String RATING_UNKNOWN = "UNKNOWN";

    // Rating thresholds on IPQS's 0-100 fraud_score. IPQS suggests treating roughly 85+ as high
    // risk and judging a number on fraud_score together with recent_abuse / VOIP / prepaid /
    // active / risky. 60 for "suspicious" is our own choice: tune both to taste.
    private static final int HIGH_RISK_SCORE   = 85;
    private static final int MEDIUM_RISK_SCORE = 60;

    @SerializedName("success")       private boolean success;
    @SerializedName("message")       private String  message;
    @SerializedName("valid")         private Boolean valid;
    @SerializedName("active")        private Boolean active;
    @SerializedName("fraud_score")   private Integer fraudScore;
    @SerializedName("recent_abuse")  private Boolean recentAbuse;
    @SerializedName("VOIP")          private Boolean voip;          // note: upper-case key in the API
    @SerializedName("prepaid")       private Boolean prepaid;
    @SerializedName("risky")         private Boolean risky;
    @SerializedName("spammer")       private Boolean spammer;
    @SerializedName("carrier")       private String  carrier;
    @SerializedName("line_type")     private String  lineType;
    @SerializedName("country")       private String  country;
    @SerializedName("dialing_code")  private Integer dialingCode;
    @SerializedName("active_status") private String  activeStatus;

    // ── API status ────────────────────────────────────────────────

    public boolean isSuccess()   { return success; }
    public String  getMessage()  { return message; }

    /** IPQS answered fine but says the number itself isn't valid. */
    public boolean isInvalidNumber() { return success && Boolean.FALSE.equals(valid); }

    // ── Signals ───────────────────────────────────────────────────

    public boolean hasRiskScore()    { return fraudScore != null; }
    public int     getRiskScore()    { return fraudScore != null ? fraudScore : 0; }   // 0-100

    public boolean hasRecentAbuse()  { return Boolean.TRUE.equals(recentAbuse); }
    public boolean isSpammer()       { return Boolean.TRUE.equals(spammer); }
    public boolean isRisky()         { return Boolean.TRUE.equals(risky); }
    public boolean isPrepaid()       { return Boolean.TRUE.equals(prepaid); }
    public boolean isDisconnected()  { return Boolean.FALSE.equals(active); }

    public boolean isVoip() {
        return Boolean.TRUE.equals(voip) || "VOIP".equalsIgnoreCase(lineType);
    }

    /** The warning signs found, in plain words (also saved with the number in "My lists"). */
    public List<String> getWarningSigns() {
        List<String> signs = new ArrayList<>();
        if (hasRecentAbuse()) signs.add("Linked to recent fraud or abuse");
        if (isSpammer())      signs.add("Flagged as a spammer");
        if (isRisky())        signs.add("Flagged as risky");
        if (isDisconnected()) signs.add("Not an active line (could be spoofed)");
        if (isPrepaid())      signs.add("Prepaid line");
        if (isVoip())         signs.add("Internet (VoIP) number");
        return signs;
    }

    // ── Display helpers used by GuardianFragment ──────────────────

    public String getCarrierName() {
        return isBlank(carrier) || "N/A".equalsIgnoreCase(carrier) ? "Unknown" : carrier;
    }

    public String getPhoneTypeDescription() {
        return isBlank(lineType) || "N/A".equalsIgnoreCase(lineType) ? "Unknown" : lineType;
    }

    public String getCountryCode() {
        return dialingCode != null ? "+" + dialingCode : "";
    }

    /**
     * Maps IPQS's signals onto the app's rating tiers:
     *   MALICIOUS  : recent abuse, flagged spammer, or fraud_score >= 85
     *   SUSPICIOUS : fraud_score >= 60, flagged risky, or the line isn't active (a call "from" a
     *                disconnected number may be spoofed)
     *   SAFE       : valid number with a score and no warning signs
     *   UNKNOWN    : lookup failed, number not recognised, or no score returned
     */
    public String getAppRating() {
        if (!success || Boolean.FALSE.equals(valid)) return RATING_UNKNOWN;

        if (hasRecentAbuse() || isSpammer() || getRiskScore() >= HIGH_RISK_SCORE) {
            return Constants.RATING_MALICIOUS;
        }
        if (getRiskScore() >= MEDIUM_RISK_SCORE || isRisky() || isDisconnected()) {
            return Constants.RATING_SUSPICIOUS;
        }
        return hasRiskScore() ? Constants.RATING_SAFE : RATING_UNKNOWN;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

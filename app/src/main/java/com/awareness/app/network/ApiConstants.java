package com.awareness.app.network;

import com.awareness.app.BuildConfig;

/**
 * API configuration. No secrets live in source code: both keys are injected at build time
 * from local.properties (which is git-ignored):
 *
 *     SAFE_BROWSING_API_KEY=...
 *     IPQS_API_KEY=...
 *
 * Because these keys ship inside the APK, anyone determined can extract them. Limit the damage:
 *  - Safe Browsing: restrict the key to "Safe Browsing API" only in Google Cloud Console.
 *  - IPQualityScore: watch your usage in the IPQS dashboard, and regenerate the key there if
 *    you ever see unexpected lookups.
 */
public class ApiConstants {

    // ── Google Safe Browsing (URL scanner) ───────────────────────
    public static final String SAFE_BROWSING_BASE_URL = "https://safebrowsing.googleapis.com/";
    public static final String SAFE_BROWSING_API_KEY  = BuildConfig.SAFE_BROWSING_API_KEY;

    // ── IPQualityScore (phone checker) ───────────────────────────
    public static final String IPQS_BASE_URL = "https://ipqualityscore.com/";
    public static final String IPQS_API_KEY  = BuildConfig.IPQS_API_KEY;

    /**
     * IPQS "strictness" (0-2): higher = stricter fraud scoring but more false positives.
     * 1 is a balanced middle value; lower it to 0 if too many ordinary numbers look risky.
     */
    public static final int IPQS_STRICTNESS = 1;
}

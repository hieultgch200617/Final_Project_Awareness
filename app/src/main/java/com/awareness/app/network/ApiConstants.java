package com.awareness.app.network;

import com.awareness.app.BuildConfig;

/**
 * API configuration.
 *
 * No secrets are written in source code any more:
 *  - SAFE_BROWSING_API_KEY is injected at build time from local.properties (which is git-ignored).
 *    Add this line to local.properties:   SAFE_BROWSING_API_KEY=your_key_here
 *    Then restrict the key in Google Cloud Console: API restriction = "Safe Browsing API" only.
 *    Do NOT add an Android-app restriction to this key: this REST call doesn't send the app-identity
 *    headers, so every scan would fail with HTTP 403. Set a quota cap instead.
 *
 *  - Telesign credentials are NOT in the app at all. Phone checks go through the
 *    "checkPhone" Cloud Function, which holds the credentials as Firebase secrets.
 */
public class ApiConstants {

    // ── Google Safe Browsing ─────────────────────────────────────
    public static final String SAFE_BROWSING_BASE_URL = "https://safebrowsing.googleapis.com/";
    public static final String SAFE_BROWSING_API_KEY  = BuildConfig.SAFE_BROWSING_API_KEY;

    // ── Cloud Functions ──────────────────────────────────────────
    // Must match the region in functions/index.js
    public static final String FUNCTIONS_REGION = "asia-southeast1";
}

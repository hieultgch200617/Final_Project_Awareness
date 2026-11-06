package com.awareness.app.utils;

public class Constants {

    // ── Firestore Collection Names ──────────────────────────────
    // These are the "tables" in your Firebase database
    public static final String COLLECTION_USERS       = "users";
    public static final String COLLECTION_LESSONS     = "lessons";
    public static final String COLLECTION_QUIZZES     = "quizzes";
    public static final String COLLECTION_REPORTS     = "scam_reports";
    public static final String COLLECTION_SCANS       = "scan_logs";       // URL & phone scans
    public static final String COLLECTION_LEADERBOARD = "leaderboard";

    // ── User Roles ──────────────────────────────────────────────
    public static final String ROLE_USER  = "user";
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_STAFF = "staff";

    // ── Scam Categories (for Academy lessons) ───────────────────
    public static final String CATEGORY_PHISHING      = "Phishing";
    public static final String CATEGORY_VOIP          = "VoIP / Caller ID";
    public static final String CATEGORY_DEEPFAKE      = "Deepfakes";
    public static final String CATEGORY_ROMANCE       = "Romance Scams";
    public static final String CATEGORY_INVESTMENT    = "Investment Fraud";

    // ── Lesson Types ────────────────────────────────────────────
    public static final String LESSON_TYPE_TEXT  = "text";
    public static final String LESSON_TYPE_IMAGE = "image";
    public static final String LESSON_TYPE_VIDEO = "video";

    // ── Guardian Tool: Safety Ratings ───────────────────────────
    public static final String RATING_SAFE        = "SAFE";
    public static final String RATING_SUSPICIOUS  = "SUSPICIOUS";
    public static final String RATING_MALICIOUS   = "MALICIOUS";

    // ── SharedPreferences Keys ───────────────────────────────────
    // SharedPreferences is like a small local storage (similar to localStorage in JS)
    public static final String PREFS_NAME            = "awareness_prefs";
    public static final String PREF_USER_ID          = "user_id";
    public static final String PREF_USER_ROLE        = "user_role";
    public static final String PREF_STREAK_COUNT     = "streak_count";
    public static final String PREF_LAST_ACTIVE_DATE = "last_active_date";
    public static final String PREF_DAILY_QUIZ_DONE  = "daily_quiz_done";

    // ── Intent Extras (data passed between screens) ──────────────
    public static final String EXTRA_LESSON_ID   = "lesson_id";
    public static final String EXTRA_CATEGORY    = "category";
    public static final String EXTRA_REPORT_ID   = "report_id";

    // ── Gamification ────────────────────────────────────────────
    public static final int XP_PER_LESSON       = 10;
    public static final int XP_PER_SCAM_LAB     = 25;
    public static final int XP_PER_DAILY_QUIZ   = 15;
    public static final int XP_PER_RED_FLAG     = 5;   // Tapping a red flag in Scam Lab

    // ── Notification Channel ────────────────────────────────────
    public static final String NOTIF_CHANNEL_ID   = "daily_quiz_channel";
    public static final String NOTIF_CHANNEL_NAME = "Daily Quiz";
}
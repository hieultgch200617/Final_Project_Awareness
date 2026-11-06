package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;

public class LeaderboardEntry {

    @DocumentId
    private String userId;

    private String displayName;
    private int    xpPoints;
    private int    level;
    private String region;
    private int    streakCount;

    // rank is NOT stored in Firestore — we calculate it
    // client-side based on the sorted list position
    private int rank;

    // ── Required no-arg constructor ──────────────────────────────
    public LeaderboardEntry() {}

    // ── Getters & Setters ─────────────────────────────────────────

    public String getUserId()                      { return userId; }
    public void   setUserId(String userId)         { this.userId = userId; }

    public String getDisplayName()                         { return displayName; }
    public void   setDisplayName(String displayName)       { this.displayName = displayName; }

    public int  getXpPoints()                      { return xpPoints; }
    public void setXpPoints(int xpPoints)          { this.xpPoints = xpPoints; }

    public int  getLevel()                         { return level; }
    public void setLevel(int level)                { this.level = level; }

    public String getRegion()                      { return region; }
    public void   setRegion(String region)         { this.region = region; }

    public int  getStreakCount()                   { return streakCount; }
    public void setStreakCount(int streakCount)    { this.streakCount = streakCount; }

    public int  getRank()                          { return rank; }
    public void setRank(int rank)                  { this.rank = rank; }

    // ── Helper: first letter of name for avatar circle ───────────
    public String getAvatarLetter() {
        if (displayName != null && !displayName.isEmpty()) {
            return displayName.substring(0, 1).toUpperCase();
        }
        return "?";
    }
}

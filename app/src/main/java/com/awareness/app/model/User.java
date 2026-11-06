package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class User {

    @DocumentId                 // Firestore auto-fills this with the document ID
    private String userId;

    private String displayName;
    private String email;
    private String role;        // "user", "admin", or "staff"
    private String avatarUrl;   // Profile picture stored in Firebase Storage

    // Gamification fields
    private int    xpPoints;
    private int    level;
    private int    streakCount;
    private String lastActiveDate;   // "yyyy-MM-dd" format
    private int    lessonsCompleted;
    private int    quizzesCompleted;

    // Location (for Community Alert Map context)
    private String region;           // e.g. "Ho Chi Minh City", "Hanoi"

    private boolean isBanned;

    @ServerTimestamp                 // Firebase sets this automatically when doc is created
    private Date createdAt;

    // ── No-argument constructor (REQUIRED by Firebase) ───────────
    public User() {}

    // ── Constructor for creating a new user on registration ──────
    public User(String userId, String displayName, String email) {
        this.userId       = userId;
        this.displayName  = displayName;
        this.email        = email;
        this.role         = "user";     // Everyone starts as a regular user
        this.xpPoints     = 0;
        this.level        = 1;
        this.streakCount  = 0;
        this.isBanned     = false;
    }

    // ── Getters & Setters ────────────────────────────────────────
    // Firebase needs getters to READ data, and setters to WRITE data

    public String getUserId()                  { return userId; }
    public void   setUserId(String userId)     { this.userId = userId; }

    public String getDisplayName()                       { return displayName; }
    public void   setDisplayName(String displayName)     { this.displayName = displayName; }

    public String getEmail()                   { return email; }
    public void   setEmail(String email)       { this.email = email; }

    public String getRole()                    { return role; }
    public void   setRole(String role)         { this.role = role; }

    public String getAvatarUrl()                       { return avatarUrl; }
    public void   setAvatarUrl(String avatarUrl)       { this.avatarUrl = avatarUrl; }

    public int  getXpPoints()                  { return xpPoints; }
    public void setXpPoints(int xpPoints)      { this.xpPoints = xpPoints; }

    public int  getLevel()                     { return level; }
    public void setLevel(int level)            { this.level = level; }

    public int  getStreakCount()               { return streakCount; }
    public void setStreakCount(int streakCount){ this.streakCount = streakCount; }

    public String getLastActiveDate()                          { return lastActiveDate; }
    public void   setLastActiveDate(String lastActiveDate)     { this.lastActiveDate = lastActiveDate; }

    public int  getLessonsCompleted()                      { return lessonsCompleted; }
    public void setLessonsCompleted(int lessonsCompleted)  { this.lessonsCompleted = lessonsCompleted; }

    public int  getQuizzesCompleted()                      { return quizzesCompleted; }
    public void setQuizzesCompleted(int quizzesCompleted)  { this.quizzesCompleted = quizzesCompleted; }

    public String getRegion()                  { return region; }
    public void   setRegion(String region)     { this.region = region; }

    @PropertyName("isBanned")   // without this Firestore stores the field as "banned"
    public boolean isBanned()                  { return isBanned; }
    @PropertyName("isBanned")
    public void    setBanned(boolean banned)   { isBanned = banned; }

    public Date getCreatedAt()                 { return createdAt; }
    public void setCreatedAt(Date createdAt)   { this.createdAt = createdAt; }
}
 
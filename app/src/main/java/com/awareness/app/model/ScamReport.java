package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.PropertyName;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class ScamReport {

    @DocumentId
    private String reportId;

    private String scamType;        // e.g. "Phishing", "VoIP / Caller ID"
    private String description;     // User's description of the scam
    private String region;          // Vietnamese province
    private double latitude;        // For map pin
    private double longitude;
    private String reportedBy;      // userId of reporter (not shown publicly)
    private int    upvotes;         // Community upvote count
    private boolean isVerified;     // Admin-verified report

    @ServerTimestamp
    private Date reportedAt;

    public ScamReport() {}

    // ── Getters & Setters ─────────────────────────────────────────

    public String getReportId()                        { return reportId; }
    public void   setReportId(String reportId)         { this.reportId = reportId; }

    public String getScamType()                        { return scamType; }
    public void   setScamType(String scamType)         { this.scamType = scamType; }

    public String getDescription()                     { return description; }
    public void   setDescription(String description)   { this.description = description; }

    public String getRegion()                          { return region; }
    public void   setRegion(String region)             { this.region = region; }

    public double getLatitude()                        { return latitude; }
    public void   setLatitude(double latitude)         { this.latitude = latitude; }

    public double getLongitude()                       { return longitude; }
    public void   setLongitude(double longitude)       { this.longitude = longitude; }

    public String getReportedBy()                      { return reportedBy; }
    public void   setReportedBy(String reportedBy)     { this.reportedBy = reportedBy; }

    public int  getUpvotes()                           { return upvotes; }
    public void setUpvotes(int upvotes)                { this.upvotes = upvotes; }

    @PropertyName("isVerified")   // CommunityFragment writes "isVerified"; default mapping would read "verified"
    public boolean isVerified()                        { return isVerified; }
    @PropertyName("isVerified")
    public void    setVerified(boolean verified)       { isVerified = verified; }

    public Date getReportedAt()                        { return reportedAt; }
    public void setReportedAt(Date reportedAt)         { this.reportedAt = reportedAt; }
}

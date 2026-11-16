package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Lesson {

    /** type value for true/false question lessons (older lessons use text / image / video). */
    public static final String TYPE_TRUE_FALSE = "true_false";

    @DocumentId
    private String lessonId;

    private String title;           // e.g. "How Phishing Emails Work"
    private String category;        // e.g. "Phishing" — matches Constants.CATEGORY_*
    private String type;            // "text", "image", or "video"
    private String content;         // The lesson body text
    private String mediaUrl;        // URL for image or video (stored in Firebase Storage)
    private String thumbnailUrl;    // Preview image for the lesson card
    private int    xpReward;        // XP earned on completion (default: 10)
    private int    orderIndex;      // Controls the order lessons appear in the list
    private boolean isPublished;    // Admin can toggle this — unpublished lessons are hidden

    // ── True/false lessons only (null on older lessons) ──────────
    // For type == "true_false": `content` holds the STATEMENT the learner judges.
    private Boolean correctAnswer;  // true = the statement is true
    private String  explanation;    // shown after the learner answers

    @ServerTimestamp
    private Date createdAt;

    // ── Required no-arg constructor for Firebase ─────────────────
    public Lesson() {}

    // ── Getters & Setters ─────────────────────────────────────────

    public String getLessonId()                    { return lessonId; }
    public void   setLessonId(String lessonId)     { this.lessonId = lessonId; }

    public String getTitle()                       { return title; }
    public void   setTitle(String title)           { this.title = title; }

    public String getCategory()                    { return category; }
    public void   setCategory(String category)     { this.category = category; }

    public String getType()                        { return type; }
    public void   setType(String type)             { this.type = type; }

    public String getContent()                     { return content; }
    public void   setContent(String content)       { this.content = content; }

    public String getMediaUrl()                    { return mediaUrl; }
    public void   setMediaUrl(String mediaUrl)     { this.mediaUrl = mediaUrl; }

    public String getThumbnailUrl()                        { return thumbnailUrl; }
    public void   setThumbnailUrl(String thumbnailUrl)     { this.thumbnailUrl = thumbnailUrl; }

    public int  getXpReward()                      { return xpReward; }
    public void setXpReward(int xpReward)          { this.xpReward = xpReward; }

    public int  getOrderIndex()                    { return orderIndex; }
    public void setOrderIndex(int orderIndex)      { this.orderIndex = orderIndex; }

    public boolean isPublished()                   { return isPublished; }
    public void    setPublished(boolean published) { isPublished = published; }

    public Boolean getCorrectAnswer()                      { return correctAnswer; }
    public void    setCorrectAnswer(Boolean correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getExplanation()                         { return explanation; }
    public void   setExplanation(String explanation)       { this.explanation = explanation; }

    public Date getCreatedAt()                     { return createdAt; }
    public void setCreatedAt(Date createdAt)       { this.createdAt = createdAt; }
}

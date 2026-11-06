package com.awareness.app.model;

import com.google.firebase.firestore.DocumentId;
import java.util.List;

public class ScamScenario {

    @DocumentId
    private String scenarioId;

    private String title;           // e.g. "Suspicious Bank SMS"
    private String category;        // e.g. "Phishing", "VoIP / Caller ID"
    private String scenarioType;    // "sms", "email", "webpage"
    private String senderLabel;     // e.g. "Vietcombank" or "+84 912 345 678"
    private String message;         // The full body text of the fake scam message
    private int    xpReward;        // Base XP for completing (bonus per flag found)
    private List<RedFlag> redFlags; // The hidden suspicious elements

    // ── Required no-arg constructor ──────────────────────────────
    public ScamScenario() {}

    // ── Inner class: RedFlag ─────────────────────────────────────
    // A RedFlag is a suspicious segment inside the message text.
    // start/end are character indices into the message string.
    public static class RedFlag {
        private int    start;           // Start index in message text
        private int    end;             // End index in message text
        private String label;           // Short name e.g. "Suspicious URL"
        private String explanation;     // Full explanation shown after submit

        public RedFlag() {}

        public int    getStart()                   { return start; }
        public void   setStart(int start)          { this.start = start; }

        public int    getEnd()                     { return end; }
        public void   setEnd(int end)              { this.end = end; }

        public String getLabel()                   { return label; }
        public void   setLabel(String label)       { this.label = label; }

        public String getExplanation()                         { return explanation; }
        public void   setExplanation(String explanation)       { this.explanation = explanation; }
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getScenarioId()                          { return scenarioId; }
    public void   setScenarioId(String scenarioId)         { this.scenarioId = scenarioId; }

    public String getTitle()                               { return title; }
    public void   setTitle(String title)                   { this.title = title; }

    public String getCategory()                            { return category; }
    public void   setCategory(String category)             { this.category = category; }

    public String getScenarioType()                        { return scenarioType; }
    public void   setScenarioType(String scenarioType)     { this.scenarioType = scenarioType; }

    public String getSenderLabel()                         { return senderLabel; }
    public void   setSenderLabel(String senderLabel)       { this.senderLabel = senderLabel; }

    public String getMessage()                             { return message; }
    public void   setMessage(String message)               { this.message = message; }

    public int    getXpReward()                            { return xpReward; }
    public void   setXpReward(int xpReward)                { this.xpReward = xpReward; }

    public List<RedFlag> getRedFlags()                     { return redFlags; }
    public void          setRedFlags(List<RedFlag> flags)  { this.redFlags = flags; }
}
